#include "climate.h"
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/event_groups.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "host/ble_sm.h"
#include "host/util/util.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

void ble_store_config_init(void);
static uint8_t address_type;
static uint32_t pin;
static EventGroupHandle_t control;
static char input[1536], output[512]="{\"ok\":true}";
static size_t used;
static bool stopping;
static const ble_uuid128_t service=BLE_UUID128_INIT(
    0x01,0xa0,0xb1,0x3c,0xf5,0x57,0x31,0x9b,0xb8,0x4c,0x4b,0x7d,0x01,0x00,0xc1,0xa2);
static const ble_uuid128_t rpc=BLE_UUID128_INIT(
    0x01,0xa0,0xb1,0x3c,0xf5,0x57,0x31,0x9b,0xb8,0x4c,0x4b,0x7d,0x02,0x00,0xc1,0xa2);

static void process(void)
{
    cJSON *r=cJSON_Parse(input), *reply=cJSON_CreateObject();
    const cJSON *cmd=cJSON_GetObjectItemCaseSensitive(r,"cmd");
    bool ok=cJSON_IsString(cmd);
    const char *error="unknown_command";
    if (ok && strcmp(cmd->valuestring,"get_status")==0) {
        cJSON_AddStringToObject(reply,"device_id",cl_device_id);
        cJSON_AddStringToObject(reply,"firmware",CL_VERSION);
        cJSON_AddBoolToObject(reply,"simulated",true);
        const cl_sample_t *s=&cl_buffer.samples[cl_buffer.count-1];
        cJSON_AddNumberToObject(reply,"temperature_c",s->temperature);
        cJSON_AddNumberToObject(reply,"humidity_pct",s->humidity);
        cJSON_AddNullToObject(reply,"battery_mv");
        cJSON_AddNumberToObject(reply,"queued",cl_buffer.count);
        cJSON_AddNumberToObject(reply,"failures",cl_buffer.failures);
        cJSON_AddNumberToObject(reply,"dropped",cl_buffer.dropped);
    } else if (ok && strcmp(cmd->valuestring,"get_config")==0) {
        cJSON_AddStringToObject(reply,"ssid",cl_config.ssid);
        cJSON_AddStringToObject(reply,"server_url",cl_config.url);
        cJSON_AddNumberToObject(reply,"sample_s",cl_config.sample_s);
        cJSON_AddNumberToObject(reply,"upload_every",cl_config.upload_every);
        cJSON_AddBoolToObject(reply,"allow_http",cl_config.allow_http);
        cJSON_AddBoolToObject(reply,"has_password",cl_config.password[0]!=0);
        cJSON_AddBoolToObject(reply,"has_token",cl_config.token[0]!=0);
    } else if (ok && strcmp(cmd->valuestring,"set_config")==0) {
        ok=cl_config_update(r,&error)==ESP_OK;
    } else if (ok && strcmp(cmd->valuestring,"run")==0) {
        // Stop is triggered only when Android has read the acknowledgment.
        cJSON_AddBoolToObject(reply,"will_sleep",true);
    } else ok=false;
    cJSON_AddBoolToObject(reply,"ok",ok);
    if (!ok) cJSON_AddStringToObject(reply,"error",error);
    if (!cJSON_PrintPreallocated(reply,output,sizeof(output),false))
        strcpy(output,"{\"ok\":false,\"error\":\"response_too_large\"}");
    cJSON_Delete(reply); cJSON_Delete(r);
}

static int access_rpc(uint16_t conn,uint16_t attr,struct ble_gatt_access_ctxt *ctxt,void *arg)
{
    (void)conn; (void)attr; (void)arg;
    if (ctxt->op==BLE_GATT_ACCESS_OP_READ_CHR) {
        int rc=os_mbuf_append(ctxt->om,output,strlen(output));
        if (!rc && strstr(output,"\"will_sleep\":true")) xEventGroupSetBits(control,BIT0);
        return rc?BLE_ATT_ERR_INSUFFICIENT_RES:0;
    }
    uint16_t n=OS_MBUF_PKTLEN(ctxt->om);
    if (!n || used+n>=sizeof(input)) { used=0; return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN; }
    if (os_mbuf_copydata(ctxt->om,0,n,input+used)) return BLE_ATT_ERR_UNLIKELY;
    // A JSON frame ends with LF. Raw NULs or multiple frames are rejected.
    if (memchr(input+used,0,n)) { used=0; return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN; }
    used+=n; input[used]=0;
    char *lf=memchr(input,'\n',used);
    if (lf) {
        if (lf!=input+used-1) { used=0; return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN; }
        *lf=0; process(); used=0;
    }
    return 0;
}

static const struct ble_gatt_svc_def services[]={
    {.type=BLE_GATT_SVC_TYPE_PRIMARY,.uuid=&service.u,
     .characteristics=(struct ble_gatt_chr_def[]){
       {.uuid=&rpc.u,.access_cb=access_rpc,.min_key_size=16,
        .flags=BLE_GATT_CHR_F_READ|BLE_GATT_CHR_F_WRITE|
               BLE_GATT_CHR_F_READ_ENC|BLE_GATT_CHR_F_WRITE_ENC|
               BLE_GATT_CHR_F_READ_AUTHEN|BLE_GATT_CHR_F_WRITE_AUTHEN}, {0}}}, {0}};
static void advertise(void);
static void check_ble(int rc) { if(rc) { printf("BLE error: %d\n",rc); abort(); } }
static int gap_event(struct ble_gap_event *event,void *arg)
{
    (void)arg;
    switch(event->type) {
    case BLE_GAP_EVENT_DISCONNECT: used=0; strcpy(output,"{\"ok\":true}"); advertise(); break;
    case BLE_GAP_EVENT_CONNECT: if(event->connect.status) advertise(); break;
    case BLE_GAP_EVENT_ADV_COMPLETE: advertise(); break;
    case BLE_GAP_EVENT_REPEAT_PAIRING: return BLE_GAP_REPEAT_PAIRING_IGNORE;
    case BLE_GAP_EVENT_PASSKEY_ACTION:
        if(event->passkey.params.action==BLE_SM_IOACT_DISP) {
            struct ble_sm_io io={.action=BLE_SM_IOACT_DISP,.passkey=pin};
            ble_sm_inject_io(event->passkey.conn_handle,&io);
        }
        break;
    default: break;
    }
    return 0;
}
static void advertise(void)
{
    if(stopping) return;
    struct ble_hs_adv_fields f={0};
    f.flags=BLE_HS_ADV_F_DISC_GEN|BLE_HS_ADV_F_BREDR_UNSUP;
    f.uuids128=(ble_uuid128_t *)&service; f.num_uuids128=1; f.uuids128_is_complete=1;
    check_ble(ble_gap_adv_set_fields(&f));
    struct ble_hs_adv_fields scan={0};
    const char *name=ble_svc_gap_device_name();
    scan.name=(uint8_t *)name; scan.name_len=strlen(name); scan.name_is_complete=1;
    check_ble(ble_gap_adv_rsp_set_fields(&scan));
    struct ble_gap_adv_params p={.conn_mode=BLE_GAP_CONN_MODE_UND,.disc_mode=BLE_GAP_DISC_MODE_GEN};
    int rc=ble_gap_adv_start(address_type,NULL,BLE_HS_FOREVER,&p,gap_event,NULL);
    if(rc) printf("BLE advertising failed: %d\n",rc);
}
static void sync_host(void)
{
    check_ble(ble_hs_util_ensure_addr(0));
    check_ble(ble_hs_id_infer_auto(0,&address_type));
    advertise();
}
static void host_task(void *arg) { (void)arg; nimble_port_run(); nimble_port_freertos_deinit(); }
void cl_setup_run(void)
{
    pin=cl_pairing_pin(); control=xEventGroupCreate(); assert(control);
    printf("BLE setup: %d seconds. Pairing PIN: %06lu\n",CL_SETUP_SECONDS,(unsigned long)pin);
    ESP_ERROR_CHECK(nimble_port_init());
    ble_hs_cfg.sync_cb=sync_host;
    ble_hs_cfg.store_status_cb=ble_store_util_status_rr;
    ble_hs_cfg.sm_io_cap=BLE_HS_IO_DISPLAY_ONLY;
    ble_hs_cfg.sm_bonding=1; ble_hs_cfg.sm_mitm=1; ble_hs_cfg.sm_sc=1;
    ble_hs_cfg.sm_our_key_dist=BLE_SM_PAIR_KEY_DIST_ENC|BLE_SM_PAIR_KEY_DIST_ID;
    ble_hs_cfg.sm_their_key_dist=BLE_SM_PAIR_KEY_DIST_ENC|BLE_SM_PAIR_KEY_DIST_ID;
    ble_svc_gap_init(); ble_svc_gatt_init();
    check_ble(ble_gatts_count_cfg(services)); check_ble(ble_gatts_add_svcs(services));
    char name[24]; snprintf(name,sizeof(name),"ClimateLink-%.6s",cl_device_id+9);
    check_ble(ble_svc_gap_device_name_set(name));
    ble_store_config_init(); nimble_port_freertos_init(host_task);
    xEventGroupWaitBits(control,BIT0,pdFALSE,pdFALSE,pdMS_TO_TICKS(CL_SETUP_SECONDS*1000));
    vTaskDelay(pdMS_TO_TICKS(500)); // Give final GATT read time to reach the phone.
    stopping=true;
    nimble_port_stop(); nimble_port_deinit(); vEventGroupDelete(control);
}
