#include "climate.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "esp_event.h"
#include "esp_wifi.h"
#include "esp_netif.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "esp_netif_sntp.h"

static EventGroupHandle_t events;
static void wifi_event(void *a,esp_event_base_t base,int32_t id,void *data)
{
    (void)a; (void)data;
    if (base==IP_EVENT && id==IP_EVENT_STA_GOT_IP) xEventGroupSetBits(events,BIT0);
    // One bounded connection attempt per cycle; no reconnect loop.
}

bool cl_upload(void)
{
    if (!cl_config.allow_http && strncmp(cl_config.url,"https://",8)) return false;
    events=xEventGroupCreate();
    if (!events) return false;
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_t *netif=esp_netif_create_default_wifi_sta();
    wifi_init_config_t init=WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&init));
    esp_event_handler_instance_t handler;
    ESP_ERROR_CHECK(esp_event_handler_instance_register(IP_EVENT,IP_EVENT_STA_GOT_IP,wifi_event,NULL,&handler));
    ESP_ERROR_CHECK(esp_wifi_set_storage(WIFI_STORAGE_RAM));
    wifi_config_t cfg={0};
    memcpy(cfg.sta.ssid,cl_config.ssid,strlen(cl_config.ssid));
    memcpy(cfg.sta.password,cl_config.password,strlen(cl_config.password));
    cfg.sta.threshold.authmode=cl_config.password[0]?WIFI_AUTH_WPA2_PSK:WIFI_AUTH_OPEN;
    cfg.sta.pmf_cfg.capable=true;
    cfg.sta.failure_retry_cnt=1;
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA,&cfg));
    ESP_ERROR_CHECK(esp_wifi_start());
    ESP_ERROR_CHECK(esp_wifi_connect());
    bool ok=false;
    if (!(xEventGroupWaitBits(events,BIT0,pdFALSE,pdFALSE,pdMS_TO_TICKS(15000))&BIT0)) goto done;
    if (time(NULL)<1704067200) {
        esp_sntp_config_t sntp=ESP_NETIF_SNTP_DEFAULT_CONFIG("pool.ntp.org");
        if (esp_netif_sntp_init(&sntp)==ESP_OK) {
            esp_netif_sntp_sync_wait(pdMS_TO_TICKS(5000));
            esp_netif_sntp_deinit();
        }
    }
    cJSON *root=cJSON_CreateObject(), *samples=cJSON_CreateArray();
    if (!root || !samples) { cJSON_Delete(root); cJSON_Delete(samples); goto done; }
    cJSON_AddNumberToObject(root,"schema_version",1);
    cJSON_AddStringToObject(root,"device_id",cl_device_id);
    char session[9]; snprintf(session,sizeof(session),"%08lx",(unsigned long)cl_buffer.session);
    cJSON_AddStringToObject(root,"session_id",session);
    cJSON_AddStringToObject(root,"firmware",CL_VERSION);
    cJSON_AddBoolToObject(root,"simulated",true);
    cJSON_AddNullToObject(root,"battery_mv");
    cJSON_AddNumberToObject(root,"dropped_samples",cl_buffer.dropped);
    cJSON_AddItemToObject(root,"samples",samples);
    for (uint32_t i=0;i<cl_buffer.count;++i) {
        const cl_sample_t *s=&cl_buffer.samples[i];
        cJSON *v=cJSON_CreateObject();
        cJSON_AddNumberToObject(v,"sequence",s->seq);
        if (s->timestamp) cJSON_AddNumberToObject(v,"timestamp",(double)s->timestamp);
        else cJSON_AddNullToObject(v,"timestamp");
        cJSON_AddNumberToObject(v,"temperature_c",s->temperature);
        cJSON_AddNumberToObject(v,"humidity_pct",s->humidity);
        cJSON_AddItemToArray(samples,v);
    }
    char *body=cJSON_PrintUnformatted(root); cJSON_Delete(root);
    if (!body) goto done;
    esp_http_client_config_t http={.url=cl_config.url,.timeout_ms=8000,
        .crt_bundle_attach=esp_crt_bundle_attach,.disable_auto_redirect=true};
    esp_http_client_handle_t client=esp_http_client_init(&http);
    if (client) {
        esp_http_client_set_method(client,HTTP_METHOD_POST);
        esp_http_client_set_header(client,"Content-Type","application/json");
        if (cl_config.token[0]) {
            char auth[140]; snprintf(auth,sizeof(auth),"Bearer %s",cl_config.token);
            esp_http_client_set_header(client,"Authorization",auth);
        }
        esp_http_client_set_post_field(client,body,strlen(body));
        esp_err_t err=esp_http_client_perform(client);
        int status=esp_http_client_get_status_code(client);
        ok=err==ESP_OK && status>=200 && status<300;
        printf("Upload: %s, HTTP %d\n",esp_err_to_name(err),status);
        esp_http_client_cleanup(client);
    }
    free(body);
done:
    esp_wifi_disconnect(); esp_wifi_stop(); esp_wifi_deinit();
    esp_event_handler_instance_unregister(IP_EVENT,IP_EVENT_STA_GOT_IP,handler);
    esp_netif_destroy_default_wifi(netif);
    esp_event_loop_delete_default(); vEventGroupDelete(events);
    return ok;
}
