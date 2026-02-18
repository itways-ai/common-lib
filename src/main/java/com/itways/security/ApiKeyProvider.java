package com.itways.security;

import com.itways.common.exception.InvalidApiKeyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ApiKeyProvider {
    private static final String PREFIX = "sk_live_";
    private static final String SEPARATOR = "::";

    public String getUsernameFromApiKey(String apiKey) {
        return getParts(apiKey)[2];
    }

    public String getAccountIdHashedFromApiKey(String apiKey) {
        return getParts(apiKey)[0];
    }

    public String getAccountIdEncryptedFromApiKey(String apiKey) {
        return getParts(apiKey)[1];
    }

    public int getKeyVersion(String apiKey) {
        try {
            return Integer.parseInt(getParts(apiKey)[3]);
        } catch (Exception e) {
            return 0;
        }
    }

    private String[] getParts(String apiKey) {
        if (apiKey == null || !apiKey.startsWith(PREFIX)) {
            throw new InvalidApiKeyException("Invalid API Key format");
        }
        try {
            String encryptedB64 = apiKey.substring(PREFIX.length());
            String decrypted = SecurityUtils.decrypt(encryptedB64);
            if (decrypted == null) {
                throw new InvalidApiKeyException();
            }
            // Parts: accHash :: accEnc :: userEnc :: keyVersion :: padding
            return decrypted.split(SEPARATOR);
        } catch (Exception e) {
            log.error("Failed to parse API key", e);
            throw new InvalidApiKeyException();
        }
    }
}
