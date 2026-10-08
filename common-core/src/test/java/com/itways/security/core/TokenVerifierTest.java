package com.itways.security.core;

import static com.itways.security.core.TestTokens.FOREIGN;
import static com.itways.security.core.TestTokens.PLATFORM;
import static com.itways.security.core.TestTokens.WEBHOOK;
import static com.itways.security.core.TestTokens.WEBHOOK_PREVIOUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.itways.security.core.TokenVerifier.Failure;
import com.itways.security.core.TokenVerifier.Kind;
import com.itways.security.core.TokenVerifier.Result;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenVerifierTest {

    private static final String KID = TokenVerifier.WEBHOOK_KEY_ID;

    /** Platform key, webhook key and the previous webhook key (a rotation under way). */
    private final TokenVerifier verifier = new TokenVerifier(PLATFORM.getPublic(), WEBHOOK.getPublic(),
            WEBHOOK_PREVIOUS.getPublic(), null);

    @Nested
    class Kinds {

        @Test
        void aTokenWithoutTypeIsAnAccessToken() {
            Result result = verifier.check(TestTokens.accessToken());

            assertThat(result.kind()).isEqualTo(Kind.ACCESS);
            assertThat(result.isVerified()).isTrue();
            assertThat(result.failure()).isNull();
            assertThat(result.type()).isNull();
            assertThat(result.claims().getSubject()).isEqualTo(TestTokens.USERNAME);
        }

        @Test
        void refreshAndWebhookTokensAreTheirOwnKinds() {
            assertThat(verifier.check(TestTokens.refreshToken()).kind()).isEqualTo(Kind.REFRESH);
            Result webhook = verifier.check(TestTokens.webhookToken(WEBHOOK.getPrivate(), KID));
            assertThat(webhook.kind()).isEqualTo(Kind.CHANNEL_WEBHOOK);
            assertThat(webhook.type()).isEqualTo("CHANNEL_WEBHOOK");
        }

        @Test
        void anExplicitAccessTypeIsAccessAndAnUnknownTypeIsOther() {
            assertThat(verifier.check(TestTokens.typed(PLATFORM.getPrivate(), null, "ACCESS")).kind())
                    .isEqualTo(Kind.ACCESS);
            Result other = verifier.check(TestTokens.typed(PLATFORM.getPrivate(), null, "SOMETHING"));
            assertThat(other.kind()).isEqualTo(Kind.OTHER);
            assertThat(other.isVerified()).isTrue();
        }

        @Test
        void aTypeThatIsNotAStringIsInvalid() {
            String numericType = TestTokens.typed(PLATFORM.getPrivate(), null, 7);

            assertThat(verifier.check(numericType).failure()).isEqualTo(Failure.INVALID);
            assertThatThrownBy(() -> verifier.verify(numericType)).isInstanceOf(JwtException.class);
        }

        @Test
        void anUnknownKidMeansThePlatformKey() {
            String token = Jwts.builder().header().keyId("some-other-key").and().subject("x")
                    .expiration(java.util.Date.from(Instant.now().plusSeconds(600)))
                    .signWith(PLATFORM.getPrivate(), Jwts.SIG.RS256).compact();

            assertThat(verifier.check(token).kind()).isEqualTo(Kind.ACCESS);
        }
    }

    @Nested
    class WebhookKeyRotation {

        @Test
        void aTokenSignedWithThePreviousKeyVerifiesWhileItIsConfigured() {
            String old = TestTokens.webhookToken(WEBHOOK_PREVIOUS.getPrivate(), KID);

            assertThat(verifier.verify(old).get("channelId")).isEqualTo("c1");
            assertThat(verifier.check(old).kind()).isEqualTo(Kind.CHANNEL_WEBHOOK);
        }

        @Test
        void withoutThePreviousKeyTheOldTokenIsRefused() {
            TokenVerifier afterRotation = new TokenVerifier(PLATFORM.getPublic(), WEBHOOK.getPublic(), null, null);
            String old = TestTokens.webhookToken(WEBHOOK_PREVIOUS.getPrivate(), KID);

            assertThat(afterRotation.check(old).failure()).isEqualTo(Failure.INVALID);
            assertThat(afterRotation.check(TestTokens.webhookToken(WEBHOOK.getPrivate(), KID)).kind())
                    .isEqualTo(Kind.CHANNEL_WEBHOOK);
        }

        @Test
        void thePreviousKeyStillOnlySignsWebhookCredentials() {
            String userLookalike = TestTokens.typed(WEBHOOK_PREVIOUS.getPrivate(), KID, null);
            String refreshLookalike = TestTokens.typed(WEBHOOK_PREVIOUS.getPrivate(), KID, "REFRESH");

            assertThat(verifier.check(userLookalike).failure()).isEqualTo(Failure.INVALID);
            assertThat(verifier.check(refreshLookalike).failure()).isEqualTo(Failure.INVALID);
        }

        @Test
        void aStrangerKeyIsRefusedEvenWithAPreviousKeyConfigured() {
            assertThat(verifier.check(TestTokens.webhookToken(FOREIGN.getPrivate(), KID)).failure())
                    .isEqualTo(Failure.INVALID);
        }

        @Test
        void aPreviousKeyWithoutACurrentOneIsAConfigurationError() {
            assertThatThrownBy(() -> new TokenVerifier(PLATFORM.getPublic(), null, WEBHOOK_PREVIOUS.getPublic(), null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("CHANNEL_WEBHOOK_PUBLIC_KEY_PREVIOUS is set but CHANNEL_WEBHOOK_PUBLIC_KEY is not");
        }
    }

    @Nested
    class KeySeparation {

        @Test
        void onlyAWebhookTokenMayBeSignedWithTheWebhookKey() {
            assertThatThrownBy(() -> verifier.verify(TestTokens.typed(WEBHOOK.getPrivate(), KID, null)))
                    .isInstanceOf(JwtException.class)
                    .hasMessage("Only a channel webhook token may be signed with the webhook key");
        }

        @Test
        void aKidlessTokenSignedWithTheWebhookKeyIsInvalid() {
            String token = TestTokens.accessToken(WEBHOOK.getPrivate(), Instant.now(), Duration.ofHours(1));

            assertThat(verifier.check(token).failure()).isEqualTo(Failure.INVALID);
        }

        @Test
        void onceTheWebhookKeyExistsAPlatformSignedWebhookTokenIsInvalid() {
            assertThatThrownBy(() -> verifier.verify(TestTokens.webhookToken(PLATFORM.getPrivate(), null)))
                    .isInstanceOf(JwtException.class)
                    .hasMessage("Channel webhook tokens must be signed with the webhook key");
        }

        @Test
        void withoutAWebhookKeyAPlatformSignedWebhookTokenIsGenuine() {
            TokenVerifier platformOnly = new TokenVerifier(PLATFORM.getPublic(), null, null, null);

            assertThat(platformOnly.check(TestTokens.webhookToken(PLATFORM.getPrivate(), null)).kind())
                    .isEqualTo(Kind.CHANNEL_WEBHOOK);
            Result webhookSigned = platformOnly.check(TestTokens.webhookToken(WEBHOOK.getPrivate(), KID));
            assertThat(webhookSigned.failure()).isEqualTo(Failure.KEY_NOT_CONFIGURED);
            assertThatThrownBy(() -> platformOnly.verify(TestTokens.webhookToken(WEBHOOK.getPrivate(), KID)))
                    .isInstanceOf(TokenVerifier.KeyNotConfiguredException.class)
                    .hasMessage("Token signed with the webhook key, which is not configured here");
        }

        @Test
        void withoutThePlatformKeyAnAccessTokenCannotBeChecked() {
            TokenVerifier noKeys = new TokenVerifier(null, null, null, null);

            assertThat(noKeys.check(TestTokens.accessToken()).failure()).isEqualTo(Failure.KEY_NOT_CONFIGURED);
            assertThat(noKeys.hasPlatformKey()).isFalse();
            assertThat(noKeys.hasWebhookKey()).isFalse();
            assertThat(noKeys.hasWebhookPreviousKey()).isFalse();
        }
    }

    @Nested
    class Refusals {

        @Test
        void anExpiredTokenIsExpired() {
            String expired = TestTokens.accessToken(PLATFORM.getPrivate(), Instant.now().minus(Duration.ofHours(2)),
                    Duration.ofHours(1));

            assertThat(verifier.check(expired).failure()).isEqualTo(Failure.EXPIRED);
            assertThatThrownBy(() -> verifier.verify(expired)).isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        void expiryUsesTheGivenClock() {
            Instant issued = Instant.parse("2030-01-01T00:00:00Z");
            String token = TestTokens.accessToken(PLATFORM.getPrivate(), issued, Duration.ofHours(1));
            TokenVerifier during = new TokenVerifier(PLATFORM.getPublic(), null, null,
                    Clock.fixed(issued.plusSeconds(60), ZoneOffset.UTC));
            TokenVerifier after = new TokenVerifier(PLATFORM.getPublic(), null, null,
                    Clock.fixed(issued.plus(Duration.ofHours(2)), ZoneOffset.UTC));

            assertThat(during.check(token).kind()).isEqualTo(Kind.ACCESS);
            assertThat(after.check(token).failure()).isEqualTo(Failure.EXPIRED);
        }

        @Test
        void garbageUnsignedSymmetricAndForeignTokensAreInvalid() {
            String unsigned = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                    + Base64.getUrlEncoder().withoutPadding()
                            .encodeToString("{\"sub\":\"x\"}".getBytes(StandardCharsets.UTF_8))
                    + ".";
            String hmac = Jwts.builder().subject("x")
                    .signWith(new SecretKeySpec(new byte[32], "HmacSHA256"), Jwts.SIG.HS256).compact();
            String foreign = TestTokens.accessToken(FOREIGN.getPrivate(), Instant.now(), Duration.ofHours(1));

            for (String token : new String[] { "abc", "a.b.c", unsigned, hmac, foreign, "", null }) {
                Result result = verifier.check(token);
                assertThat(result.kind()).as(String.valueOf(token)).isEqualTo(Kind.INVALID);
                assertThat(result.failure()).isEqualTo(Failure.INVALID);
                assertThat(result.claims()).isNull();
            }
        }
    }
}
