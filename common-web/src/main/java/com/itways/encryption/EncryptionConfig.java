package com.itways.encryption;

import com.itways.annotation.EnableEncryption;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** What {@code @EnableEncryption} brings: {@link RsaService} (it used to component-scan {@code com.itways.encryption}). */
@Configuration
@ConditionalOnBean(annotation = EnableEncryption.class)
@Slf4j
@Import(RsaService.class)
public class EncryptionConfig {

    @PostConstruct
    public void print() {
        log.info("✅ Common-lib Encryption configuration initialized");
    }
}
