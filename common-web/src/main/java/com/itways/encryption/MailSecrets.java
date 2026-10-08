package com.itways.encryption;

import java.nio.charset.StandardCharsets;

/**
 * Seals the SMTP password a tenant stores on a journey's SEND_MAIL step
 * (SPC-04), so it neither rests in journey-service's tables and published
 * versions nor travels over RabbitMQ in plain text.
 *
 * <p>
 * The key ({@code MAIL_SECRETS_KEY}, 32 random bytes, Base64) is held by
 * journey-service, which seals the password when a step is saved,
 * notification-service, which opens it right before it connects to the tenant's
 * mail server, and conversation-service, which seals a password still in plain text
 * (a version published before this class existed) before publishing it. The
 * journey engine passes the sealed value through and never needs the key.
 *
 * <p>
 * Format: {@code ms:<kid>:} + Base64(12-byte nonce + AES-256-GCM ciphertext and
 * tag), where {@code kid} is the first 8 hex characters of the key's SHA-256
 * ({@link SealedSecrets}, of which this is the {@code ms:} cipher with one fixed
 * context). A fixed context string is the associated data, so a value sealed by
 * another cipher under the same key never passes for a mail secret. Values
 * sealed before 2.3.0, when this class held its own copy of the cipher, open
 * unchanged.
 *
 * <p>
 * Rotation: set the new key as current and the old one as previous; values
 * under the previous key keep opening, and journey-service re-seals them under
 * the current key at startup. Then drop the previous key. A value without the
 * {@code ms:} prefix predates this class: {@link #open} returns it as it is and
 * {@link #seal} seals it.
 */
public class MailSecrets extends SealedSecrets {

    public static final String PREFIX = "ms:";

    private static final String KEY_VARIABLE = "MAIL_SECRETS_KEY";
    private static final byte[] CONTEXT = "mail-secret".getBytes(StandardCharsets.UTF_8);

    /**
     * @param currentKeyBase64  required: the key new values are sealed with
     * @param previousKeyBase64 optional: a retired key still accepted for opening
     */
    public MailSecrets(String currentKeyBase64, String previousKeyBase64) {
        super(PREFIX, currentKey(currentKeyBase64), previousKey(previousKeyBase64), "mail secret");
    }

    private static byte[] currentKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(KEY_VARIABLE + " is not set. Tenants' SMTP passwords are sealed "
                    + "with it; generate one with: openssl rand -base64 32");
        }
        return decodeKey(base64, KEY_VARIABLE);
    }

    private static byte[] previousKey(String base64) {
        return base64 == null || base64.isBlank() ? null : decodeKey(base64, KEY_VARIABLE + "_PREVIOUS");
    }

    /** Whether {@code value} is a sealed value (under any key). Needs no key. */
    public static boolean isSealed(String value) {
        return isSealed(value, PREFIX);
    }

    /**
     * The value sealed under the current key. Idempotent: an already sealed value
     * (under any key) is returned unchanged, so a client can send back what it
     * was given. Null and empty (no password: a relay without authentication)
     * are returned as they are.
     */
    public String seal(String plain) {
        if (plain == null || plain.isEmpty() || isSealed(plain)) {
            return plain;
        }
        return seal(plain, CONTEXT);
    }

    /**
     * The plain value of a value sealed under the current or previous key. A
     * value without the prefix predates sealing and is returned as it is.
     *
     * @throws IllegalStateException for an unknown key, a wrong key or a
     *                               tampered value; the message never holds the
     *                               value
     */
    public String open(String value) {
        if (!isSealed(value)) {
            return value;
        }
        return open(value, CONTEXT);
    }

    /**
     * The value sealed under the current key: a plain value is sealed, one under
     * the previous key is opened and sealed again, one already current is
     * returned unchanged. What a startup backfill or a key rotation runs.
     */
    public String reseal(String value) {
        if (value == null || value.isEmpty() || isCurrent(value)) {
            return value;
        }
        return seal(open(value));
    }
}
