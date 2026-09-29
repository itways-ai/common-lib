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

import com.itways.security.SecurityUtils;

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
 * tag), where {@code kid} is the first 8 hex characters of the key's SHA-256.
 * A fixed context string is the associated data, so a value sealed by another
 * cipher under the same key never passes for a channel secret.
 *
 * <p>
 * Rotation: set the new key as current and the old one as previous; values
 * under the previous key keep decrypting, and channels-service re-encrypts them
 * under the current key at startup. Then drop the previous key. A value
 * without the {@code cs:} prefix predates this class and is read with
 * {@link SecurityUtils#decrypt(String)}.
 */
public class ChannelSecrets {

	public static final String PREFIX = "cs:";

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int KEY_BYTES = 32;
	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final byte[] CONTEXT = "channel-secret".getBytes(StandardCharsets.UTF_8);

	private final Key current;
	private final Key previous;
	private final SecureRandom random = new SecureRandom();

	/**
	 * @param currentKeyBase64  required: the key new values are encrypted with
	 * @param previousKeyBase64 optional: a retired key still accepted for reading
	 */
	public ChannelSecrets(String currentKeyBase64, String previousKeyBase64) {
		if (currentKeyBase64 == null || currentKeyBase64.isBlank()) {
			throw new IllegalStateException("CHANNEL_SECRETS_KEY is not set. Channel provider secrets are encrypted "
					+ "with it; generate one with: openssl rand -base64 32");
		}
		this.current = Key.parse(currentKeyBase64, "CHANNEL_SECRETS_KEY");
		this.previous = previousKeyBase64 == null || previousKeyBase64.isBlank() ? null
				: Key.parse(previousKeyBase64, "CHANNEL_SECRETS_KEY_PREVIOUS");
	}

	public String encrypt(String plain) {
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
			throw new IllegalStateException("Could not encrypt the channel secret", e);
		}
	}

	/** Reads values under the current key, the previous key, or the legacy platform key. */
	public String decrypt(String stored) {
		if (stored == null || !stored.startsWith(PREFIX)) {
			return SecurityUtils.decrypt(stored);
		}
		int colon = stored.indexOf(':', PREFIX.length());
		String kid = colon < 0 ? "" : stored.substring(PREFIX.length(), colon);
		Key key = current.id().equals(kid) ? current : previous != null && previous.id().equals(kid) ? previous : null;
		if (key == null) {
			throw new IllegalStateException("Channel secret was encrypted with an unknown key (" + kid + ")");
		}
		try {
			byte[] in = Base64.getDecoder().decode(stored.substring(colon + 1));
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key.secret(), new GCMParameterSpec(TAG_BITS, in, 0, NONCE_BYTES));
			cipher.updateAAD(CONTEXT);
			return new String(cipher.doFinal(in, NONCE_BYTES, in.length - NONCE_BYTES), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			// Never the value itself: a wrong key or a tampered value ends here.
			throw new IllegalStateException("Could not decrypt the channel secret (" + e.getClass().getSimpleName() + ")");
		}
	}

	/** True when {@code stored} is already under the current key; otherwise it should be re-encrypted. */
	public boolean isCurrent(String stored) {
		return stored != null && stored.startsWith(PREFIX + current.id() + ":");
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
