package com.itways.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itways.security.SecurityUtils;
import com.itways.security.SessionRevocationStore;
import com.itways.security.jwt.JwtTokenProvider;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PLT-05: the JWT filter with the real {@link SessionRevocationStore} on a
 * mocked Redis. Pins the Redis keys auth-service writes, the one read per
 * request, the shared 401 and the fail-open behaviour.
 */
class AccessTokenRevocationTest {

    private static final KeyPair PLATFORM = rsa();
    private static final String ACCOUNT_ID = "4242";
    private static final String SID = "sid-1";
    private static final String CUTOFF_KEY = "nibras:auth:pwchanged:" + ACCOUNT_ID;
    private static final String SESSION_KEY = "nibras:auth:revoked-session:" + SID;

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private JwtTokenProvider tokens;
    private SessionRevocationStore store;
    private JwtAuthenticationFilter filter;
    private ListAppender<ILoggingEvent> storeLog;

    @BeforeEach
    void setUp() {
        new SecurityUtils().setEncryptionKey("revocation-test-secret");
        tokens = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokens, "publicKeyStr", encode(PLATFORM.getPublic().getEncoded()));
        ReflectionTestUtils.setField(tokens, "privateKeyStr", encode(PLATFORM.getPrivate().getEncoded()));
        ReflectionTestUtils.setField(tokens, "accessExpiration", 900_000L);
        ReflectionTestUtils.setField(tokens, "refreshExpiration", 60_000L);
        tokens.init();
        when(redis.opsForValue()).thenReturn(values);
        store = storeWith(redis);
        filter = filterWith(store);
        storeLog = new ListAppender<>();
        storeLog.start();
        ((Logger) LoggerFactory.getLogger(SessionRevocationStore.class)).addAppender(storeLog);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(SessionRevocationStore.class)).detachAppender(storeLog);
        SecurityContextHolder.clearContext();
    }

    @Test
    void aSignedOutSessionsTokenIs401WithOneMget() throws Exception {
        when(values.multiGet(List.of(CUTOFF_KEY, SESSION_KEY))).thenReturn(Arrays.asList(null, "1"));

        Outcome outcome = run(accessToken(SID));

        assertThat(outcome.chainCalled()).isFalse();
        assertThat(outcome.response().getStatus()).isEqualTo(401);
        assertThat(outcome.body().get("errorCode").asText()).isEqualTo("AUTH_401");
        assertThat(outcome.body().get("message").asText()).isEqualTo(JwtAuthenticationFilter.SESSION_ENDED_MESSAGE);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(values, never()).get(anyString());
    }

    @Test
    void aTokenIssuedBeforeTheAccountCutoffIs401() throws Exception {
        long cutoff = Instant.now().plusSeconds(60).getEpochSecond(); // e.g. deactivated just now
        when(values.multiGet(List.of(CUTOFF_KEY, SESSION_KEY))).thenReturn(Arrays.asList(Long.toString(cutoff), null));

        Outcome outcome = run(accessToken(SID));

        assertThat(outcome.chainCalled()).isFalse();
        assertThat(outcome.response().getStatus()).isEqualTo(401);
        assertThat(outcome.body().get("errorCode").asText()).isEqualTo("AUTH_401");
    }

    @Test
    void aTokenIssuedAfterTheCutoffIsAllowed() throws Exception {
        long cutoff = Instant.now().minusSeconds(60).getEpochSecond(); // the pair minted by the change itself
        when(values.multiGet(List.of(CUTOFF_KEY, SESSION_KEY))).thenReturn(Arrays.asList(Long.toString(cutoff), null));

        Outcome outcome = run(accessToken(SID));

        assertThat(outcome.chainCalled()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void aTokenWithoutSidIsCheckedAgainstTheCutoffOnlyAndAllowed() throws Exception {
        Outcome outcome = run(accessToken(null));

        assertThat(outcome.chainCalled()).isTrue();
        verify(values).get(CUTOFF_KEY);
        verify(values, never()).multiGet(anyList());
    }

    @Test
    void redisDownFailsOpenWithOneWarnPerMinute() throws Exception {
        when(values.multiGet(anyList())).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThat(run(accessToken(SID)).chainCalled()).isTrue();
        assertThat(run(accessToken(SID)).chainCalled()).isTrue();

        List<ILoggingEvent> warns = storeLog.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
        assertThat(warns).hasSize(1);
        assertThat(warns.get(0).getFormattedMessage()).contains("Redis unreachable").contains("not enforced");
    }

    @Test
    void aServiceWithoutRedisStillAuthenticates() throws Exception {
        JwtAuthenticationFilter withoutRedis = filterWith(storeWith(null));

        Outcome outcome = run(withoutRedis, accessToken(SID));

        assertThat(outcome.chainCalled()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void revokeSessionKeepsTheEntryForTheAccessTokenLifetimePlusAMargin() {
        store.revokeSession(SID, Duration.ofMinutes(15));
        store.revokeSession("sid-2", null);

        verify(values).set(SESSION_KEY, "1", Duration.ofMinutes(16));
        verify(values).set("nibras:auth:revoked-session:sid-2", "1", Duration.ofDays(2));
    }

    @Test
    void revokeAllBeforeStoresTheCutoffInEpochSeconds() {
        Instant cutoff = Instant.parse("2026-09-27T10:00:00.750Z");

        store.revokeAllBefore(ACCOUNT_ID, cutoff);

        verify(values).set(CUTOFF_KEY, Long.toString(cutoff.getEpochSecond()), Duration.ofDays(2));
    }

    @Test
    void writesNeverThrowWhenRedisIsDown() {
        doThrow(new RedisConnectionFailureException("down")).when(values).set(anyString(), anyString(),
                any(Duration.class));

        assertThatCode(() -> {
            store.revokeSession(SID, Duration.ofMinutes(15));
            store.revokeAllBefore(ACCOUNT_ID, Instant.now());
            storeWith(null).revokeSession(SID, Duration.ofMinutes(15));
        }).doesNotThrowAnyException();
        verify(values).set(eq(SESSION_KEY), eq("1"), any(Duration.class));
    }

    private record Outcome(boolean chainCalled, MockHttpServletResponse response) {
        JsonNode body() throws Exception {
            return new ObjectMapper().readTree(response.getContentAsString());
        }
    }

    private Outcome run(String token) throws Exception {
        return run(filter, token);
    }

    private static Outcome run(JwtAuthenticationFilter target, String token) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        target.doFilter(request, response, chain);
        return new Outcome(chain.getRequest() != null, response);
    }

    private String accessToken(String sid) {
        Map<String, Object> claims = new HashMap<>(Map.of("role", "USER", "accH", SecurityUtils.hash(ACCOUNT_ID),
                "accE", SecurityUtils.encrypt(ACCOUNT_ID)));
        if (sid != null) {
            claims.put(JwtTokenProvider.CLAIM_SESSION_ID, sid);
        }
        return tokens.generateAccessToken("user@example.test", claims);
    }

    @SuppressWarnings("unchecked")
    private static SessionRevocationStore storeWith(StringRedisTemplate template) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        return new SessionRevocationStore(provider, "2d");
    }

    @SuppressWarnings("unchecked")
    private JwtAuthenticationFilter filterWith(SessionRevocationStore revocations) {
        ObjectProvider<ObjectMapper> mappers = mock(ObjectProvider.class);
        when(mappers.getIfUnique(any())).thenAnswer(call -> ((Supplier<ObjectMapper>) call.getArgument(0)).get());
        return new JwtAuthenticationFilter(tokens, revocations, mappers);
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
