package com.itways.encryption;

import com.itways.annotation.EnableEncryption;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(annotation = EnableEncryption.class)
@Slf4j
@ComponentScan("com.itways.encryption")
public class EncryptionConfig {

    @PostConstruct
    public void print() {
        log.info("✅ Common-lib Encryption configuration initialized");
    }
}
