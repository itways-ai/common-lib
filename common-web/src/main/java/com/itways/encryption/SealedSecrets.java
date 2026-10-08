package com.itways.encryption;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A single-key AES-256-GCM cipher for one kind of tenant secret: the one
 * implementation behind {@link MailSecrets} ({@code ms:}), {@link ChannelSecrets}
 * ({@code cs:}) and {@link ConnectorSecrets} ({@code is:}), which used to be
 * three copies of it.
 *
 * <p>
 * Format: {@code <prefix><kid>:} + Base64(12-byte nonce + ciphertext and
 * 128-bit tag), where {@code kid} is the first 8 hex characters of the key's
 * SHA-256. The prefix says which cipher a stored value belongs to; the
 * associated data ({@code aad}, supplied per call) binds a value to its use, so
 * a ciphertext cannot be opened under another context: a mail password never
 * passes for a channel token under the same key, and a connector secret
 * copied from one row to another ({@code instanceId|accountId|field}) does not
 * open there. The prefix is not part of the associated data; two ciphers with
 * the same key and the same context would open each other's values, so each
 * kind of secret has its own context.
 *
 * <p>
 * Rotation: set the new key as current and the old one as previous; values
 * under the previous key keep opening ({@link #isCurrent} says which ones), a
 * maintenance job {@link #reseal reseals} them under the current key, and then
 * the previous key is dropped. The subclasses read their keys from one
 * environment variable each ({@code MAIL_SECRETS_KEY}, {@code CHANNEL_SECRETS_KEY},
 * {@code CONNECTOR_SECRETS_KEY}, plus {@code _PREVIOUS}), 32 random bytes in
 * Base64 ({@code openssl rand -base64 32}).
 *
 * <p>
 * The subclasses keep their own conveniences on top (a fixed context, a static
 * {@code isSealed}, pass-through of legacy plain values); this class treats
 * every value as required and sealed. A failure message never holds the value
 * or the key: a wrong key or a tampered value ends in an
 * {@link IllegalStateException} that names the kind of secret and the
 * exception class.
 */
public class SealedSecrets {

    /** AES-256: the key is exactly this long. */
    public static final int KEY_BYTES = 32;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;

    private final String prefix;
    private final String label;
    private final Key current;
    private final Key previous;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param prefix      what a sealed value starts with, one or more characters
     *                    and a colon: {@code "is:"}
     * @param currentKey  required, {@value #KEY_BYTES} bytes: the key new values
     *                    are sealed with
     * @param previousKey optional: a retired key still accepted for opening
     * @throws IllegalArgumentException for a prefix without the colon
     * @throws IllegalStateException    for a key of the wrong length
     */
    public SealedSecrets(String prefix, byte[] currentKey, byte[] previousKey) {
        this(prefix, currentKey, previousKey, "secret");
    }

    /**
     * For subclasses: as the public constructor, with {@code label} ("mail
     * secret", "channel secret") naming the kind of secret in failure messages.
     */
    protected SealedSecrets(String prefix, byte[] currentKey, byte[] previousKey, String label) {
        if (prefix == null || prefix.length() < 2 || !prefix.endsWith(":")) {
            throw new IllegalArgumentException("The prefix must be at least one character followed by ':', like \"ms:\"");
        }
        this.prefix = prefix;
        this.label = label == null || label.isBlank() ? "secret" : label;
        this.current = Key.of(requireKeyLength(currentKey, "current key"));
        this.previous = previousKey == null ? null : Key.of(requireKeyLength(previousKey, "previous key"));
    }

    /**
     * The key bytes of a Base64 setting, checked for length; {@code variable}
     * is the setting's name for the message ({@code MAIL_SECRETS_KEY}).
     *
     * @throws IllegalStateException when the value is not Base64 or not
     *                               {@value #KEY_BYTES} bytes
     */
    public static byte[] decodeKey(String base64, String variable) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(variable + " is not valid Base64");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException(variable + " must be " + KEY_BYTES
                    + " bytes (openssl rand -base64 32), got " + bytes.length);
        }
        return bytes;
    }

    /**
     * Associated data from identifiers: the parts joined with {@code |} as
     * UTF-8, {@code instanceId|accountId|field} for a connector secret. A
     * part may not be null, blank or hold a {@code |}, so two different sets of
     * parts never give the same bytes.
     */
    public static byte[] context(String... parts) {
        if (parts == null || parts.length == 0) {
            throw new IllegalArgumentException("The context needs at least one part");
        }
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                throw new IllegalArgumentException("A context part is null or blank");
            }
            if (part.indexOf('|') >= 0) {
                throw new IllegalArgumentException("A context part holds the separator '|'");
            }
            if (joined.length() > 0) {
                joined.append('|');
            }
            joined.append(part);
        }
        return joined.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Whether {@code value} starts with {@code prefix}: a sealed value of that cipher, under any key. Needs no key. */
    public static boolean isSealed(String value, String prefix) {
        return value != null && prefix != null && value.startsWith(prefix);
    }

    /** The prefix sealed values of this cipher start with. */
    public final String prefix() {
        return prefix;
    }

    /** The id of the current key (first 8 hex characters of its SHA-256), as sealed values carry it. For diagnostics. */
    public final String currentKeyId() {
        return current.id();
    }

    /** Whether {@code value} is a sealed value of this cipher (under any key). */
    public final boolean isSealedValue(String value) {
        return isSealed(value, prefix);
    }

    /**
     * True when {@code value} is sealed under the current key; a value under the
     * previous key, or not sealed at all, should be (re-)sealed.
     */
    public final boolean isCurrent(String value) {
        return value != null && value.startsWith(prefix + current.id() + ":");
    }

    /**
     * {@code plain} sealed under the current key, bound to {@code aad}. A fresh
     * nonce every time: sealing the same value twice gives two different
     * strings that both open.
     *
     * @param plain the value; never null (an empty string is sealed as such)
     * @param aad   the context the value is bound to; {@link #context} builds
     *              one from identifiers, a subclass may fix one
     */
    public String seal(String plain, byte[] aad) {
        Objects.requireNonNull(plain, "plain");
        Objects.requireNonNull(aad, "aad");
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, current.secret(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array();
            return prefix + current.id() + ":" + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not seal the " + label, e);
        }
    }

    /**
     * The plain value of {@code sealed}, a value sealed under the current or
     * previous key with the same {@code aad}.
     *
     * @throws IllegalStateException when the value does not carry this cipher's
     *                               prefix, was sealed under an unknown key, was
     *                               tampered with, or was bound to another
     *                               context; the message never holds the value
     */
    public String open(String sealed, byte[] aad) {
        Objects.requireNonNull(aad, "aad");
        if (!isSealedValue(sealed)) {
            throw new IllegalStateException(
                    "Not a sealed " + label + ": expected a value starting with '" + prefix + "'");
        }
        int colon = sealed.indexOf(':', prefix.length());
        String kid = colon < 0 ? "" : sealed.substring(prefix.length(), colon);
        Key key = current.id().equals(kid) ? current : previous != null && previous.id().equals(kid) ? previous : null;
        if (key == null) {
            throw new IllegalStateException(capitalised(label) + " was sealed with an unknown key (" + kid + ")");
        }
        try {
            byte[] in = Base64.getDecoder().decode(sealed.substring(colon + 1));
            if (in.length < NONCE_BYTES + TAG_BYTES) {
                throw new IllegalArgumentException("truncated");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key.secret(), new GCMParameterSpec(TAG_BITS, in, 0, NONCE_BYTES));
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(in, NONCE_BYTES, in.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Never the value itself: a wrong key, another context or a tampered value ends here.
            throw new IllegalStateException(
                    "Could not open the " + label + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    /**
     * {@code value} sealed under the current key: one under the previous key is
     * opened and sealed again, one already current is returned unchanged. What a
     * key rotation's maintenance job runs over every stored row.
     */
    public String reseal(String value, byte[] aad) {
        if (isCurrent(value)) {
            return value;
        }
        return seal(open(value, aad), aad);
    }

    private static byte[] requireKeyLength(byte[] key, String what) {
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalStateException("The " + what + " must be " + KEY_BYTES + " bytes, got "
                    + (key == null ? "none" : String.valueOf(key.length)));
        }
        return key;
    }

    private static String capitalised(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private record Key(SecretKey secret, String id) {

        static Key of(byte[] bytes) {
            try {
                String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 8);
                return new Key(new SecretKeySpec(bytes, "AES"), id);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }
    }
}
