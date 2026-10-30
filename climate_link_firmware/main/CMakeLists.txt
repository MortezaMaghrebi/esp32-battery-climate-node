idf_component_register(SRCS "app_main.c" "config.c" "ble_setup.c" "network.c"
    INCLUDE_DIRS "."
    REQUIRES nvs_flash esp_wifi esp_netif esp_event esp_http_client esp-tls
             mbedtls json bt esp_driver_gpio esp_hw_support esp_timer lwip)
