package com.itways.security.core;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA public keys as the platform configures them: Base64 of the X.509 DER
 * encoding ({@code RSA_PUBLIC_KEY}, {@code CHANNEL_WEBHOOK_PUBLIC_KEY}, ...).
 */
public final class PublicKeys {

    private PublicKeys() {
    }

    /**
     * Strict: plain Base64 DER, nothing else (what the services have always
     * read).
     *
     * @throws IllegalArgumentException when the value is not Base64
     * @throws GeneralSecurityException when it is not an X.509 RSA public key
     */
    public static PublicKey parse(String base64Der) throws GeneralSecurityException {
        byte[] keyBytes = Base64.getDecoder().decode(base64Der);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    /**
     * Lenient, for a value read from configuration: PEM armour and whitespace
     * are tolerated, and a blank value means "not configured".
     *
     * @param name  the setting's name, for the error message
     * @return the key, or {@code null} when {@code value} is null or blank
     * @throws IllegalStateException naming the setting, never quoting it, when
     *                               the value is not a key
     */
    public static PublicKey fromConfig(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String base64 = value
                .replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return parse(base64);
        } catch (Exception e) {
            // The exception class only: a message could quote key material.
            throw new IllegalStateException(name + " is set but is not a Base64 X.509 RSA public key ("
                    + e.getClass().getSimpleName() + ")");
        }
    }
}
