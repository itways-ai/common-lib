package com.itways.encryption;

import com.itways.security.SecurityUtils;
import java.nio.charset.StandardCharsets;

/**
 * Encrypts the provider secrets a tenant stores on a channel: Telegram bot
 * tokens and webhook secrets, Twilio auth tokens (CHN-13).
 *
 * <p>
 * They used to be encrypted with {@code JWT_ENCRYPTION_KEY}, which every
 * service holds. This key ({@code CHANNEL_SECRETS_KEY}, 32 random bytes,
 * Base64) is given only to channels-service, which stores the secrets, and
 * conversation-service, which uses them to send messages.
 *
 * <p>
 * Format: {@code cs:<kid>:} + Base64(12-byte nonce + AES-256-GCM ciphertext and
 * tag), where {@code kid} is the first 8 hex characters of the key's SHA-256
 * ({@link SealedSecrets}, of which this is the {@code cs:} cipher with one fixed
 * context). A fixed context string is the associated data, so a value sealed by
 * another cipher under the same key never passes for a channel secret. Values
 * encrypted before 2.3.0, when this class held its own copy of the cipher,
 * decrypt unchanged.
 *
 * <p>
 * Rotation: set the new key as current and the old one as previous; values
 * under the previous key keep decrypting, and channels-service re-encrypts them
 * under the current key at startup. Then drop the previous key. A value
 * without the {@code cs:} prefix predates this class and is read with
 * {@link SecurityUtils#decrypt(String)}.
 */
public class ChannelSecrets extends SealedSecrets {

    public static final String PREFIX = "cs:";

    private static final String KEY_VARIABLE = "CHANNEL_SECRETS_KEY";
    private static final byte[] CONTEXT = "channel-secret".getBytes(StandardCharsets.UTF_8);

    /**
     * @param currentKeyBase64  required: the key new values are encrypted with
     * @param previousKeyBase64 optional: a retired key still accepted for reading
     */
    public ChannelSecrets(String currentKeyBase64, String previousKeyBase64) {
        super(PREFIX, currentKey(currentKeyBase64), previousKey(previousKeyBase64), "channel secret");
    }

    private static byte[] currentKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(KEY_VARIABLE + " is not set. Channel provider secrets are encrypted "
                    + "with it; generate one with: openssl rand -base64 32");
        }
        return decodeKey(base64, KEY_VARIABLE);
    }

    private static byte[] previousKey(String base64) {
        return base64 == null || base64.isBlank() ? null : decodeKey(base64, KEY_VARIABLE + "_PREVIOUS");
    }

    public String encrypt(String plain) {
        return seal(plain, CONTEXT);
    }

    /** Reads values under the current key, the previous key, or the legacy platform key. */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return SecurityUtils.decrypt(stored);
        }
        return open(stored, CONTEXT);
    }
}
