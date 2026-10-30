#include "climate.h"
#include <stdio.h>
#include <string.h>
#include <time.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#include "driver/rtc_io.h"
#include "esp_attr.h"
#include "esp_sleep.h"
#include "esp_random.h"
#include "esp_mac.h"
#include "esp_timer.h"
#include "nvs_flash.h"

RTC_DATA_ATTR cl_buffer_t cl_buffer;
char cl_device_id[18];
// From Main.SchDoc: REL1=19, REL2=25 (also RS485_2 DE), RS485 DE=18.
static const gpio_num_t outputs[] = {GPIO_NUM_19, GPIO_NUM_25, GPIO_NUM_18};

void cl_sleep(void)
{
    for (unsigned i=0; i<sizeof(outputs)/sizeof(outputs[0]); ++i) {
        gpio_set_level(outputs[i],0);
        gpio_hold_en(outputs[i]);
    }
    gpio_deep_sleep_hold_en();
    // GPIO34 has no internal pull-up; R43 on this revision provides the pull-up.
    // Skip ext0 while button is held to avoid an immediate wake loop.
    if (gpio_get_level(CL_BUTTON)) ESP_ERROR_CHECK(esp_sleep_enable_ext0_wakeup(CL_BUTTON,0));
    ESP_ERROR_CHECK(esp_sleep_enable_timer_wakeup((uint64_t)cl_config.sample_s*1000000ULL));
    printf("Sleeping for %lu seconds\n",(unsigned long)cl_config.sample_s);
    fflush(stdout);
    esp_deep_sleep_start();
    __builtin_unreachable();
}

static void deadline(void *arg) { (void)arg; cl_sleep(); }

void app_main(void)
{
    esp_err_t err=nvs_flash_init();
    // Preserve credentials on NVS errors. Recovery is an explicit erase-flash operation.
    ESP_ERROR_CHECK(err);
    cl_config_load();
    gpio_deep_sleep_hold_dis();
    for (unsigned i=0; i<sizeof(outputs)/sizeof(outputs[0]); ++i) {
        gpio_hold_dis(outputs[i]); gpio_set_level(outputs[i],0);
        gpio_set_direction(outputs[i],GPIO_MODE_OUTPUT);
    }
    rtc_gpio_deinit(CL_BUTTON);
    gpio_set_direction(CL_BUTTON,GPIO_MODE_INPUT);
    uint8_t mac[6]; ESP_ERROR_CHECK(esp_read_mac(mac,ESP_MAC_WIFI_STA));
    snprintf(cl_device_id,sizeof(cl_device_id),"CL-%02X%02X%02X%02X%02X%02X",
        mac[0],mac[1],mac[2],mac[3],mac[4],mac[5]);
    esp_sleep_wakeup_cause_t wake=esp_sleep_get_wakeup_cause();
    if (cl_buffer.magic != 0x434C3031 || wake == ESP_SLEEP_WAKEUP_UNDEFINED) {
        memset(&cl_buffer,0,sizeof(cl_buffer));
        cl_buffer.magic=0x434C3031; cl_buffer.session=esp_random();
    }
    if (cl_buffer.count >= CL_CAPACITY) {
        memmove(cl_buffer.samples,cl_buffer.samples+1,(CL_CAPACITY-1)*sizeof(cl_sample_t));
        cl_buffer.count=CL_CAPACITY-1; ++cl_buffer.dropped;
    }
    uint32_t seq=++cl_buffer.sequence;
    time_t now=time(NULL);
    cl_buffer.samples[cl_buffer.count++]=(cl_sample_t){
        .seq=seq,.timestamp=now>1704067200?now:0,
        .temperature=23.0f+(seq%30)*0.1f,.humidity=45.0f+(seq%40)*0.2f};
    ++cl_buffer.since_upload;
    printf("%s firmware %s — SIMULATED sensor\n",cl_device_id,CL_VERSION);
    if (wake != ESP_SLEEP_WAKEUP_TIMER) cl_setup_run();
    // All network operations share this hard 45 s energy budget, including DNS/TLS.
    esp_timer_handle_t timer;
    esp_timer_create_args_t args={.callback=deadline,.name="cycle_deadline"};
    ESP_ERROR_CHECK(esp_timer_create(&args,&timer));
    ESP_ERROR_CHECK(esp_timer_start_once(timer,45000000));
    if (cl_buffer.since_upload >= cl_config.upload_every && cl_config.ssid[0] && cl_config.url[0]) {
        if (cl_upload()) {
            cl_buffer.count=0; cl_buffer.since_upload=0; cl_buffer.failures=0;
        } else {
            ++cl_buffer.failures;
            // Retry at the next upload boundary, not at every sample wake.
            cl_buffer.since_upload=0;
        }
    }
    cl_sleep();
}
