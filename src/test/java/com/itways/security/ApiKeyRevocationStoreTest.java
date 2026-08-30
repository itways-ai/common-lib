package com.itways.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The revocation deny-list is the runtime teeth behind ApiKeyService.revokeKey:
 * account-service writes {@code nibras:apikeys:revoked:<hash>} and every
 * API-key filter checks it. Behavior under Redis outage is a deliberate
 * availability-over-lockout choice — the check WARNs and allows rather than
 * failing every API-key request on the platform.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyRevocationStore")
class ApiKeyRevocationStoreTest {

    private static final String HASH = "some-key-hash";
    private static final String REDIS_KEY = "nibras:apikeys:revoked:" + HASH;

    @Mock
    private ObjectProvider<RedisTemplate<Object, Object>> templateProvider;
    @Mock
    private RedisTemplate<Object, Object> redisTemplate;
    @Mock
    private ValueOperations<Object, Object> valueOperations;

    private ApiKeyRevocationStore store;

    @BeforeEach
    void wireStore() {
        lenient().when(templateProvider.getIfAvailable()).thenReturn(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new ApiKeyRevocationStore(templateProvider);
    }

    @Nested
    @DisplayName("revoke")
    class Revoke {

        @Test
        @DisplayName("writes the deny-list entry with a TTL bounded by the key's remaining life")
        void writesWithTtl() {
            store.revoke(HASH, Duration.ofHours(3));

            verify(valueOperations).set(REDIS_KEY, "1", Duration.ofHours(3));
        }

        @Test
        @DisplayName("a key without expiry is denied forever — no TTL on the entry")
        void writesWithoutTtl() {
            store.revoke(HASH, null);

            verify(valueOperations).set(REDIS_KEY, "1");
        }

        @Test
        @DisplayName("a Redis failure is logged but does not fail the revoke call")
        void redisFailureSwallowed() {
            doThrow(new RuntimeException("connection refused"))
                    .when(valueOperations).set(anyString(), anyString());

            assertThatCode(() -> store.revoke(HASH, null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a blank hash is ignored")
        void blankHashIgnored() {
            store.revoke("  ", null);
            store.revoke(null, null);

            verify(valueOperations, never()).set(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("isRevoked")
    class IsRevoked {

        @Test
        @DisplayName("answers from Redis by key existence")
        void answersFromRedis() {
            when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(true);

            assertThat(store.isRevoked(HASH)).isTrue();
        }

        @Test
        @DisplayName("caches the not-revoked verdict — repeated checks cost one Redis round-trip")
        void cachesAllowVerdict() {
            when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(false);

            assertThat(store.isRevoked(HASH)).isFalse();
            assertThat(store.isRevoked(HASH)).isFalse();
            assertThat(store.isRevoked(HASH)).isFalse();

            verify(redisTemplate, times(1)).hasKey(REDIS_KEY);
        }

        @Test
        @DisplayName("a local revoke() poisons the cache immediately, before any Redis read")
        void revokeUpdatesCache() {
            store.revoke(HASH, null);

            assertThat(store.isRevoked(HASH)).isTrue();
            verify(redisTemplate, never()).hasKey(anyString());
        }

        @Test
        @DisplayName("Redis unreachable → WARN and allow (availability over lockout)")
        void redisDownAllows() {
            when(redisTemplate.hasKey(REDIS_KEY)).thenThrow(new RuntimeException("connection refused"));

            assertThat(store.isRevoked(HASH)).isFalse();
        }

        @Test
        @DisplayName("no Redis configured at all → allow, never throw")
        void noRedisConfigured() {
            when(templateProvider.getIfAvailable()).thenReturn(null);
            ApiKeyRevocationStore detached = new ApiKeyRevocationStore(templateProvider);

            assertThat(detached.isRevoked(HASH)).isFalse();
            assertThatCode(() -> detached.revoke(HASH, null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null or blank hashes are never revoked")
        void blankHash() {
            assertThat(store.isRevoked(null)).isFalse();
            assertThat(store.isRevoked("")).isFalse();
        }
    }
}
