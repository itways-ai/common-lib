package com.itways.encryption;

/**
 * Seals the credentials a workspace owner stores on a connector (an API
 * key, an OAuth2 client secret, a password): the {@code is:} cipher of
 * {@link SealedSecrets}, with a context per stored row.
 *
 * <p>
 * The key ({@code CONNECTOR_SECRETS_KEY}, 32 random bytes, Base64) is held by
 * journey-service only ({@code @EnableConnectorSecrets}), which seals a value
 * when a connector is saved and opens it when the engine resolves the
 * connector for a call; the opened value travels service-to-service and stays
 * in memory on the receiving side.
 *
 * <p>
 * Format: {@code is:<kid>:} + Base64(12-byte nonce + AES-256-GCM ciphertext and
 * tag). The associated data is {@code instanceId|accountId|field} (UTF-8), so a
 * ciphertext belongs to one field of one connector of one account: copied to
 * another row, another field or another account it does not open, and a mail
 * or channel secret under the same key is not a connector secret.
 *
 * <p>
 * Rotation: set the new key as current and the old one as previous; values
 * under the previous key keep opening, a maintenance job reseals the rows where
 * {@link #isCurrent} is false, then the previous key is dropped. Unlike mail
 * passwords, there are no legacy plain values: every stored value is sealed,
 * and {@link #open} refuses anything else.
 */
public class ConnectorSecrets extends SealedSecrets {

    /** Kept as {@code is:} (from the 2.3.0 name) so values sealed before the 2.5.0 rename keep opening. */
    public static final String PREFIX = "is:";

    private static final String KEY_VARIABLE = "CONNECTOR_SECRETS_KEY";

    /**
     * @param currentKeyBase64  required: the key new values are sealed with
     * @param previousKeyBase64 optional: a retired key still accepted for opening
     */
    public ConnectorSecrets(String currentKeyBase64, String previousKeyBase64) {
        super(PREFIX, currentKey(currentKeyBase64), previousKey(previousKeyBase64), "connector secret");
    }

    private static byte[] currentKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(KEY_VARIABLE + " is not set. Connector credentials (API keys, "
                    + "client secrets, passwords) are sealed with it; generate one with: openssl rand -base64 32");
        }
        return decodeKey(base64, KEY_VARIABLE);
    }

    private static byte[] previousKey(String base64) {
        return base64 == null || base64.isBlank() ? null : decodeKey(base64, KEY_VARIABLE + "_PREVIOUS");
    }

    /** Whether {@code value} is a sealed connector secret (under any key). Needs no key. */
    public static boolean isSealed(String value) {
        return isSealed(value, PREFIX);
    }

    /**
     * The associated data of one stored secret: {@code instanceId|accountId|field}.
     * The ids as the row stores them (a UUID's canonical lower-case text), the
     * field as the connector type's descriptor names it.
     */
    public static byte[] context(String instanceId, String accountId, String field) {
        return SealedSecrets.context(instanceId, accountId, field);
    }

    /** {@code plain} sealed for the field {@code field} of connector {@code instanceId} in account {@code accountId}. */
    public String seal(String plain, String instanceId, String accountId, String field) {
        return seal(plain, context(instanceId, accountId, field));
    }

    /**
     * The plain value of {@code sealed}, stored for that field of that connector
     * of that account.
     *
     * @throws IllegalStateException when the value is not an {@code is:} value, was
     *                               sealed under an unknown key, for another row
     *                               or field, or was tampered with; the message
     *                               never holds the value
     */
    public String open(String sealed, String instanceId, String accountId, String field) {
        return open(sealed, context(instanceId, accountId, field));
    }

    /** {@code sealed} under the current key; unchanged when it already is. What the reseal job runs per row. */
    public String reseal(String sealed, String instanceId, String accountId, String field) {
        return reseal(sealed, context(instanceId, accountId, field));
    }
}
