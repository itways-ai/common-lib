package com.itways.security.core;

import java.security.GeneralSecurityException;
import java.util.Objects;
import javax.crypto.spec.SecretKeySpec;

/**
 * The {@code X-API-KEY} format, and whether a key is genuine. The one
 * implementation of these rules; {@code ApiKeyProvider}, the servlet
 * {@code ApiKeyAuthenticationFilter} and the api-gateway all use it.
 *
 * <p>
 * A key is {@code sk_live_} followed by the {@link CredentialCrypto#encrypt
 * AES-256-GCM encryption} (key: {@code JWT_ENCRYPTION_KEY}) of
 * {@code accH::accE::user::version::expiresAtMillis::padding}, where
 * {@code accE} is the encrypted account id and {@code accH} its
 * {@link CredentialCrypto#hash hash}. account-service mints it.
 *
 * <p>
 * A key is {@link Status#VALID} when it decrypts, has at least the first three
 * slots, {@code accE} decrypts to an account id whose hash is {@code accH}, and
 * the expiry (missing, unparseable or 0 = never) has not passed. Whether the
 * key is still active (not revoked) is a separate check against the Redis
 * allow-list, which is not part of the format.
 *
 * <p>
 * Immutable and thread-safe. No framework, no logging; nothing returned or
 * thrown quotes the key.
 */
public final class ApiKeyCodec {

    public static final String PREFIX = "sk_live_";
    public static final String SEPARATOR = "::";

    /** The verdict on a key's format. */
    public enum Status {
        VALID,
        /** Wrong prefix, does not decrypt, too few slots, or the account binding fails. */
        INVALID,
        /** Genuine, but past the expiry it carries. */
        EXPIRED
    }

    /**
     * The outcome of {@link #check}: the status, and for a genuine key (valid or
     * expired) the decrypted payload and the account id it is bound to.
     */
    public record Result(Status status, Payload payload, String accountId) {

        static Result invalid() {
            return new Result(Status.INVALID, null, null);
        }

        public boolean isValid() {
            return status == Status.VALID;
        }
    }

    /** The decrypted slots of a key. */
    public static final class Payload {

        private final String[] parts;

        Payload(String[] parts) {
            this.parts = parts;
        }

        /** How many {@code ::}-separated slots the payload has. */
        public int size() {
            return parts.length;
        }

        /** Slot 0, {@code accH}. */
        public String accountIdHash() {
            return parts[0];
        }

        /** Slot 1, {@code accE}. */
        public String accountIdEncrypted() {
            return parts[1];
        }

        /** Slot 2, the (encrypted) user the key was minted for. */
        public String username() {
            return parts[2];
        }

        /** Slot 3; {@code 0} when missing or unparseable. */
        public int keyVersion() {
            try {
                return Integer.parseInt(parts[3]);
            } catch (Exception e) {
                return 0;
            }
        }

        /** Slot 4, epoch millis; {@code 0} (never expires) when missing or unparseable. */
        public long expiresAtEpochMillis() {
            try {
                return Long.parseLong(parts[4]);
            } catch (Exception e) {
                return 0L;
            }
        }

        /** Whether the key's own expiry has passed at {@code nowMillis}. */
        public boolean isExpiredAt(long nowMillis) {
            long expiresAt = expiresAtEpochMillis();
            return expiresAt > 0 && expiresAt <= nowMillis;
        }
    }

    private final SecretKeySpec key;

    /** @param key the AES key, {@link CredentialCrypto#deriveKey} of {@code JWT_ENCRYPTION_KEY} */
    public ApiKeyCodec(SecretKeySpec key) {
        this.key = Objects.requireNonNull(key, "key");
    }

    /** A codec for the {@code JWT_ENCRYPTION_KEY} secret. */
    public static ApiKeyCodec fromSecret(String secret) {
        return new ApiKeyCodec(CredentialCrypto.deriveKey(secret));
    }

    /** Whether {@code apiKey} has the key prefix at all (no crypto). */
    public static boolean hasPrefix(String apiKey) {
        return apiKey != null && apiKey.startsWith(PREFIX);
    }

    /**
     * The decrypted payload of {@code apiKey}. It checks the prefix and the
     * encryption only: not the slots, the account binding or the expiry.
     *
     * @throws IllegalArgumentException when the prefix is missing or the rest is
     *                                  not Base64 ciphertext
     * @throws GeneralSecurityException when it does not decrypt under this key
     */
    public Payload decode(String apiKey) throws GeneralSecurityException {
        if (!hasPrefix(apiKey)) {
            throw new IllegalArgumentException("Invalid API Key format");
        }
        return new Payload(CredentialCrypto.decrypt(key, apiKey.substring(PREFIX.length())).split(SEPARATOR));
    }

    /**
     * The account id {@code payload} is bound to: its {@code accE} decrypted,
     * when that decrypts and hashes to its {@code accH}; otherwise {@code null}.
     */
    public String boundAccountId(Payload payload) {
        try {
            String accountId = CredentialCrypto.decrypt(key, payload.accountIdEncrypted());
            return CredentialCrypto.hash(accountId).equals(payload.accountIdHash()) ? accountId : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The whole format check of {@code apiKey}, with the expiry judged at {@code nowMillis}. Never throws. */
    public Result check(String apiKey, long nowMillis) {
        Payload payload;
        try {
            payload = decode(apiKey);
        } catch (Exception e) {
            return Result.invalid();
        }
        // accH :: accE :: user are read unconditionally by every consumer.
        if (payload.size() < 3) {
            return Result.invalid();
        }
        String accountId = boundAccountId(payload);
        if (accountId == null) {
            return Result.invalid();
        }
        Status status = payload.isExpiredAt(nowMillis) ? Status.EXPIRED : Status.VALID;
        return new Result(status, payload, accountId);
    }
}
