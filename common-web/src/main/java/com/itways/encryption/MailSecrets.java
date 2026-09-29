package com.itways.encryption;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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
 * tag), where {@code kid} is the first 8 hex characters of the key's SHA-256.
 * A fixed context string is the associated data, so a value sealed by another
 * cipher under the same key never passes for a mail secret.
 *
 * <p>
 * Rotation: set the new key as current and the old one as previous; values
 * under the previous key keep opening, and journey-service re-seals them under
 * the current key at startup. Then drop the previous key. A value without the
 * {@code ms:} prefix predates this class: {@link #open} returns it as it is and
 * {@link #seal} seals it.
 */
public class MailSecrets {

	public static final String PREFIX = "ms:";

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int KEY_BYTES = 32;
	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final byte[] CONTEXT = "mail-secret".getBytes(StandardCharsets.UTF_8);

	private final Key current;
	private final Key previous;
	private final SecureRandom random = new SecureRandom();

	/**
	 * @param currentKeyBase64  required: the key new values are sealed with
	 * @param previousKeyBase64 optional: a retired key still accepted for opening
	 */
	public MailSecrets(String currentKeyBase64, String previousKeyBase64) {
		if (currentKeyBase64 == null || currentKeyBase64.isBlank()) {
			throw new IllegalStateException("MAIL_SECRETS_KEY is not set. Tenants' SMTP passwords are sealed "
					+ "with it; generate one with: openssl rand -base64 32");
		}
		this.current = Key.parse(currentKeyBase64, "MAIL_SECRETS_KEY");
		this.previous = previousKeyBase64 == null || previousKeyBase64.isBlank() ? null
				: Key.parse(previousKeyBase64, "MAIL_SECRETS_KEY_PREVIOUS");
	}

	/** Whether {@code value} is a sealed value (under any key). Needs no key. */
	public static boolean isSealed(String value) {
		return value != null && value.startsWith(PREFIX);
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
		try {
			byte[] nonce = new byte[NONCE_BYTES];
			random.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, current.secret(), new GCMParameterSpec(TAG_BITS, nonce));
			cipher.updateAAD(CONTEXT);
			byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] out = ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array();
			return PREFIX + current.id() + ":" + Base64.getEncoder().encodeToString(out);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Could not seal the mail secret", e);
		}
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
		int colon = value.indexOf(':', PREFIX.length());
		String kid = colon < 0 ? "" : value.substring(PREFIX.length(), colon);
		Key key = current.id().equals(kid) ? current : previous != null && previous.id().equals(kid) ? previous : null;
		if (key == null) {
			throw new IllegalStateException("Mail secret was sealed with an unknown key (" + kid + ")");
		}
		try {
			byte[] in = Base64.getDecoder().decode(value.substring(colon + 1));
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key.secret(), new GCMParameterSpec(TAG_BITS, in, 0, NONCE_BYTES));
			cipher.updateAAD(CONTEXT);
			return new String(cipher.doFinal(in, NONCE_BYTES, in.length - NONCE_BYTES), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			// Never the value itself: a wrong key or a tampered value ends here.
			throw new IllegalStateException("Could not open the mail secret (" + e.getClass().getSimpleName() + ")");
		}
	}

	/**
	 * True when {@code value} is sealed under the current key; a plain value, or
	 * one under the previous key, should be (re-)sealed.
	 */
	public boolean isCurrent(String value) {
		return value != null && value.startsWith(PREFIX + current.id() + ":");
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

	private record Key(SecretKey secret, String id) {

		static Key parse(String base64, String name) {
			byte[] bytes;
			try {
				bytes = Base64.getDecoder().decode(base64.trim());
			} catch (IllegalArgumentException e) {
				throw new IllegalStateException(name + " is not valid Base64");
			}
			if (bytes.length != KEY_BYTES) {
				throw new IllegalStateException(name + " must be " + KEY_BYTES
						+ " bytes (openssl rand -base64 32), got " + bytes.length);
			}
			try {
				String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 8);
				return new Key(new SecretKeySpec(bytes, "AES"), id);
			} catch (GeneralSecurityException e) {
				throw new IllegalStateException("SHA-256 unavailable", e);
			}
		}
	}
}
