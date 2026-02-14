package com.itways.common.config;

import com.itways.common.service.EncryptionService;
import com.itways.common.service.impl.RSAEncryptionServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class EncryptionConfiguration {

    @Bean
    public EncryptionService encryptionService() {
        log.info("✅ Common-lib Encryption configuration initialized");
        return new RSAEncryptionServiceImpl();
    }
}
