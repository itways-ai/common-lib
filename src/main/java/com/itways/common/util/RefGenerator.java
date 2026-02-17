package com.itways.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Utility class for generating unique transaction reference identifiers.
 * Transaction references are used to track OTP validation sessions.
 */
@Component
@Slf4j
public class RefGenerator {
        /**
         * Generates a unique transaction reference with a custom prefix.
         *
         * @param customPrefix The prefix to use (e.g., "OAUTH", "OTP")
         * @param length       The length of the random part
         * @return A unique transaction reference string
         */
        public String generate(String customPrefix, Integer length) {
                String uuid = UUID.randomUUID().toString().replace("-", "");
                String randomPart = uuid.substring(0, Math.min(length, uuid.length()));
                String transactionRef = customPrefix + randomPart.toUpperCase();

                log.debug("Generated transaction reference: {}", transactionRef);
                return transactionRef;
        }
}
