#include "climate.h"
#include <string.h>
#include <math.h>
#include "nvs.h"
#include "esp_random.h"

cl_config_t cl_config;
static const char *NS = "climate";

static bool valid(const cl_config_t *c)
{
    size_t pn = strlen(c->password);
    bool url = c->url[0] == 0 ||
        (strncmp(c->url, "https://", 8) == 0 && strlen(c->url) > 8) ||
        (c->allow_http && strncmp(c->url, "http://", 7) == 0 && strlen(c->url) > 7);
    if (strpbrk(c->url, "\r\n\t ") || strpbrk(c->token, "\r\n")) return false;
    return c->version == 1 && c->sample_s >= 60 && c->sample_s <= 86400 &&
        c->upload_every >= 1 && c->upload_every <= CL_CAPACITY &&
        (pn == 0 || (pn >= 8 && pn <= 63)) && url;
}

void cl_config_load(void)
{
    cl_config = (cl_config_t){.version=1, .sample_s=900, .upload_every=1};
    nvs_handle_t h;
    if (nvs_open(NS, NVS_READONLY, &h) != ESP_OK) return;
    cl_config_t saved = {0};
    size_t n = sizeof(saved);
    if (nvs_get_blob(h, "config", &saved, &n) == ESP_OK && n == sizeof(saved)) {
        saved.ssid[32]=0; saved.password[64]=0; saved.url[192]=0; saved.token[128]=0;
        if (valid(&saved)) cl_config = saved;
    }
    nvs_close(h);
}

static bool text_field(const cJSON *r, const char *key, char *out, size_t capacity)
{
    const cJSON *v = cJSON_GetObjectItemCaseSensitive(r, key);
    if (!v) return true; // Omitted secrets remain unchanged; empty strings clear them.
    if (!cJSON_IsString(v) || strlen(v->valuestring) >= capacity) return false;
    strcpy(out, v->valuestring);
    return true;
}

esp_err_t cl_config_update(const cJSON *r, const char **reason)
{
    cl_config_t next = cl_config;
    *reason = "invalid_config";
    if (!text_field(r,"ssid",next.ssid,sizeof(next.ssid)) ||
        !text_field(r,"password",next.password,sizeof(next.password)) ||
        !text_field(r,"server_url",next.url,sizeof(next.url)) ||
        !text_field(r,"token",next.token,sizeof(next.token))) return ESP_ERR_INVALID_ARG;
    const cJSON *v = cJSON_GetObjectItemCaseSensitive(r,"sample_s");
    if (v) {
        if (!cJSON_IsNumber(v) || !isfinite(v->valuedouble) || v->valuedouble < 60 ||
            v->valuedouble > 86400 || floor(v->valuedouble) != v->valuedouble) return ESP_ERR_INVALID_ARG;
        next.sample_s = (uint32_t)v->valuedouble;
    }
    v=cJSON_GetObjectItemCaseSensitive(r,"upload_every");
    if (v) {
        if (!cJSON_IsNumber(v) || v->valuedouble < 1 || v->valuedouble > CL_CAPACITY ||
            floor(v->valuedouble) != v->valuedouble) return ESP_ERR_INVALID_ARG;
        next.upload_every=(uint32_t)v->valuedouble;
    }
    v=cJSON_GetObjectItemCaseSensitive(r,"allow_http");
    if (v) { if (!cJSON_IsBool(v)) return ESP_ERR_INVALID_ARG; next.allow_http=cJSON_IsTrue(v); }
    if (!valid(&next)) return ESP_ERR_INVALID_ARG;
    nvs_handle_t h;
    esp_err_t err=nvs_open(NS,NVS_READWRITE,&h);
    if (err == ESP_OK) {
        err=nvs_set_blob(h,"config",&next,sizeof(next));
        if (err == ESP_OK) err=nvs_commit(h);
        nvs_close(h);
    }
    if (err == ESP_OK) cl_config=next;
    else *reason="storage_error";
    return err;
}

uint32_t cl_pairing_pin(void)
{
    nvs_handle_t h;
    ESP_ERROR_CHECK(nvs_open(NS,NVS_READWRITE,&h));
    uint32_t pin=0;
    if (nvs_get_u32(h,"ble_pin",&pin) != ESP_OK || pin < 100000 || pin > 999999) {
        pin=100000 + esp_random()%900000;
        ESP_ERROR_CHECK(nvs_set_u32(h,"ble_pin",pin));
        ESP_ERROR_CHECK(nvs_commit(h));
    }
    nvs_close(h);
    return pin;
}
