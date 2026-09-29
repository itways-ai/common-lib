package com.itways.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.itways.contracts.channels.ChannelWebhookTokenClaims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Rotating the channel webhook key without breaking the URLs already registered (CHN-12). */
class JwtTokenProviderWebhookRotationTest {

    private final KeyPair platform = pair();
    private final KeyPair oldWebhook = pair();
    private final KeyPair newWebhook = pair();

    @Test
    void aUrlSignedWithTheRetiredKeyStillVerifiesDuringTheRotation() throws Exception {
        JwtTokenProvider verifier = verifier(newWebhook, oldWebhook);

        assertThat(verifier.getVerifiedClaims(webhookToken(oldWebhook)).get("channelId")).isEqualTo("c1");
        assertThat(verifier.getVerifiedClaims(webhookToken(newWebhook)).get("channelId")).isEqualTo("c1");
    }

    @Test
    void withoutThePreviousKeyTheOldUrlIsRefused() throws Exception {
        JwtTokenProvider verifier = verifier(newWebhook, null);

        assertThatThrownBy(() -> verifier.getVerifiedClaims(webhookToken(oldWebhook))).isInstanceOf(JwtException.class);
    }

    @Test
    void thePreviousKeyStillOnlySignsWebhookCredentials() throws Exception {
        JwtTokenProvider verifier = verifier(newWebhook, oldWebhook);
        String userLookalike = Jwts.builder().subject("someone").header().keyId(ChannelWebhookTokenClaims.KEY_ID).and()
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(oldWebhook.getPrivate()).compact();

        assertThatThrownBy(() -> verifier.getVerifiedClaims(userLookalike)).isInstanceOf(JwtException.class);
    }

    @Test
    void aStrangerKeyIsRefusedEvenWithAPreviousKeyConfigured() throws Exception {
        JwtTokenProvider verifier = verifier(newWebhook, oldWebhook);

        assertThatThrownBy(() -> verifier.getVerifiedClaims(webhookToken(pair()))).isInstanceOf(JwtException.class);
    }

    private JwtTokenProvider verifier(KeyPair current, KeyPair previous) throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "publicKeyStr", encode(platform.getPublic().getEncoded()));
        ReflectionTestUtils.setField(provider, "webhookPublicKeyStr", encode(current.getPublic().getEncoded()));
        ReflectionTestUtils.setField(provider, "webhookPreviousPublicKeyStr",
                previous == null ? "" : encode(previous.getPublic().getEncoded()));
        provider.init();
        return provider;
    }

    private static String webhookToken(KeyPair signer) {
        return Jwts.builder().subject("channel:c1").header().keyId(ChannelWebhookTokenClaims.KEY_ID).and()
                .claim(ChannelWebhookTokenClaims.CLAIM_TYPE, ChannelWebhookTokenClaims.TYPE_CHANNEL_WEBHOOK)
                .claim("channelId", "c1")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static KeyPair pair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
