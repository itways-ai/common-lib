package com.itways.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * LIB-05: a service that verifies JWTs refuses to start without a usable
 * platform key, and says which variable to set. It used to generate a
 * throwaway key pair, start, and then refuse every real token (CH-15).
 */
class JwtTokenProviderStartupTest {

	private static final KeyPair PLATFORM = rsa();
	private static final KeyPair WEBHOOK = rsa();

	@Test
	void noPublicKeyFailsTheStartupNamingTheVariable() {
		assertThatThrownBy(() -> provider("", "", "", "")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("RSA_PUBLIC_KEY").hasMessageContaining("jwt.rsa.public-key");
		assertThatThrownBy(() -> provider("  ", "", "", "")).hasMessageContaining("RSA_PUBLIC_KEY");
		assertThatThrownBy(() -> provider(null, null, null, null)).hasMessageContaining("RSA_PUBLIC_KEY");
	}

	@Test
	void aPrivateKeyWithoutThePublicKeyStillFails() {
		assertThatThrownBy(() -> provider("", encode(PLATFORM.getPrivate().getEncoded()), "", ""))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("RSA_PUBLIC_KEY");
	}

	@Test
	void aBrokenKeyFailsNamingItsVariableWithoutQuotingIt() {
		assertThatThrownBy(() -> provider("bm90LWEta2V5", "", "", "")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("RSA_PUBLIC_KEY").hasMessageNotContaining("bm90LWEta2V5");
		assertThatThrownBy(() -> provider(publicKey(PLATFORM), "bm90LWEta2V5", "", ""))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("RSA_PRIVATE_KEY")
				.hasMessageNotContaining("bm90LWEta2V5");
		assertThatThrownBy(() -> provider(publicKey(PLATFORM), "", "not a key", ""))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("CHANNEL_WEBHOOK_PUBLIC_KEY");
		assertThatThrownBy(() -> provider(publicKey(PLATFORM), "", publicKey(WEBHOOK), "not a key"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CHANNEL_WEBHOOK_PUBLIC_KEY_PREVIOUS");
	}

	@Test
	void aPreviousWebhookKeyNeedsTheCurrentOne() {
		assertThatThrownBy(() -> provider(publicKey(PLATFORM), "", "", publicKey(WEBHOOK)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CHANNEL_WEBHOOK_PUBLIC_KEY_PREVIOUS is set but CHANNEL_WEBHOOK_PUBLIC_KEY is not");
	}

	@Test
	void verifyOnlyIsEnoughAndTheWebhookKeyIsOptional() {
		JwtTokenProvider verifier = provider(publicKey(PLATFORM), "", "", "");
		JwtTokenProvider signer = provider(publicKey(PLATFORM), encode(PLATFORM.getPrivate().getEncoded()), "", "");

		String token = signer.generateAccessToken("u", Map.of("accH", "h"));

		assertThat(verifier.validateToken(token)).isTrue();
		assertThatThrownBy(() -> verifier.generateAccessToken("u", Map.of()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot mint");
	}

	@Test
	void aPemArmouredPublicKeyIsAccepted() {
		String pem = "-----BEGIN PUBLIC KEY-----\n" + publicKey(PLATFORM) + "\n-----END PUBLIC KEY-----\n";

		assertThat(provider(pem, "", publicKey(WEBHOOK), "")).isNotNull();
	}

	/** As Spring creates it: a context without the key does not start. */
	@Test
	void aContextWithoutTheKeyDoesNotStart() {
		new ApplicationContextRunner().withBean(JwtTokenProvider.class).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("RSA_PUBLIC_KEY");
		});
		new ApplicationContextRunner().withBean(JwtTokenProvider.class)
				.withPropertyValues("jwt.rsa.public-key=" + publicKey(PLATFORM))
				.run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtTokenProvider.class));
		// The variable itself works too, when a service maps no property.
		new ApplicationContextRunner().withBean(JwtTokenProvider.class)
				.withPropertyValues("RSA_PUBLIC_KEY=" + publicKey(PLATFORM))
				.run(context -> assertThat(context).hasNotFailed());
	}

	private static JwtTokenProvider provider(String publicKey, String privateKey, String webhook, String previous) {
		JwtTokenProvider provider = new JwtTokenProvider();
		ReflectionTestUtils.setField(provider, "publicKeyStr", publicKey);
		ReflectionTestUtils.setField(provider, "privateKeyStr", privateKey);
		ReflectionTestUtils.setField(provider, "webhookPublicKeyStr", webhook);
		ReflectionTestUtils.setField(provider, "webhookPreviousPublicKeyStr", previous);
		ReflectionTestUtils.setField(provider, "accessExpiration", 60_000L);
		provider.init();
		return provider;
	}

	private static String publicKey(KeyPair pair) {
		return encode(pair.getPublic().getEncoded());
	}

	private static String encode(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	private static KeyPair rsa() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
