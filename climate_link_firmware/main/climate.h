#pragma once
#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"
#include "cJSON.h"

#define CL_VERSION "0.1.0"
#define CL_CAPACITY 32
#define CL_BUTTON 34
#define CL_SETUP_SECONDS 180
#define CL_SERVICE_UUID "a2c10001-7d4b-4cb8-9b31-57f53cb1a001"
#define CL_RPC_UUID "a2c10002-7d4b-4cb8-9b31-57f53cb1a001"

typedef struct {
    uint32_t version;
    char ssid[33], password[65], url[193], token[129];
    uint32_t sample_s, upload_every;
    bool allow_http;
} cl_config_t;
typedef struct { uint32_t seq; int64_t timestamp; float temperature, humidity; } cl_sample_t;
typedef struct {
    uint32_t magic, session, sequence, count, dropped, since_upload, failures;
    cl_sample_t samples[CL_CAPACITY];
} cl_buffer_t;
extern cl_config_t cl_config;
extern cl_buffer_t cl_buffer;
extern char cl_device_id[18];
void cl_config_load(void);
esp_err_t cl_config_update(const cJSON *request, const char **reason);
uint32_t cl_pairing_pin(void);
void cl_setup_run(void);
bool cl_upload(void);
void cl_sleep(void) __attribute__((noreturn));
