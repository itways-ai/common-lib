package com.itways.common.config;

import com.itways.common.handler.CustomErrorController;
import com.itways.common.handler.DataAccessExceptionHandler;
import com.itways.common.handler.GlobalExceptionHandler;
import com.itways.scope.LegacyAssistantHeaderConfig;
import com.itways.web.correlation.RequestCorrelationConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * What {@code @EnableCommon} brings: the shared error handling, the
 * OpenAPI and time configuration, request correlation (ARC-25; the
 * {@code requestIdFilter}, off with {@code itways.request-id.enabled=false})
 * and, while the assistant header is renamed (2.1.0), the filter that reads the
 * legacy name as the new one ({@link LegacyAssistantHeaderConfig}).
 * Listed explicitly (it used to component-scan {@code com.itways.common}); the
 * bean names are the ones the scan gave.
 */
@Slf4j
@Configuration
@Import({ GlobalExceptionHandler.class, DataAccessExceptionHandler.class, CustomErrorController.class,
        SwaggerConfig.class, TimeConfig.class, RequestCorrelationConfig.class, LegacyAssistantHeaderConfig.class })
public class CommonConfig {

    @PostConstruct
    public void print() {
        log.info("✅ Common-lib shared common configuration initialized");
    }
}
