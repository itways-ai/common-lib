package com.itways.cache.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisStore is the shared-cache leg of the hybrid provider. Two properties are
 * contract here: every key is namespaced as {@code cacheName + '.' + key}
 * (all services share one Redis, so the prefix is the only tenant wall between
 * caches), and no Redis failure ever propagates — a cache outage must degrade
 * to misses, never take the business call down with it. The null-template
 * guards cover the wiring mode where Redis is simply not configured.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisStore")
class RedisStoreTest {

    @Mock
    private RedisTemplate<Object, Object> redisTemplate;

    @Mock
    private ValueOperations<Object, Object> valueOps;

    private RedisStore<String, String> store() {
        return new RedisStore<>(redisTemplate, "session", 5);
    }

    @Nested
    @DisplayName("key namespacing (cacheName + '.' + key)")
    class Namespacing {

        @Test
        @DisplayName("put writes under the namespaced key with the store's TTL in minutes")
        void putNamespacesAndAppliesTtl() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            store().put("k1", "v1");

            verify(valueOps).set("session.k1", "v1", 5L, TimeUnit.MINUTES);
        }

        @Test
        @DisplayName("get reads the namespaced key and wraps hits and misses in Optional")
        void getNamespaces() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("session.k1")).thenReturn("v1");

            RedisStore<String, String> store = store();
            assertThat(store.get("k1")).contains("v1");
            assertThat(store.get("k2")).isEmpty(); // unstubbed → null → miss
        }

        @Test
        @DisplayName("containsKey asks Redis for the namespaced key")
        void containsKeyNamespaces() {
            when(redisTemplate.hasKey("session.k1")).thenReturn(true);

            assertThat(store().containsKey("k1")).isTrue();
        }

        @Test
        @DisplayName("remove deletes the namespaced key")
        void removeNamespaces() {
            store().remove("k1");

            verify(redisTemplate).delete("session.k1");
        }

        @Test
        @DisplayName("clear deletes exactly the keys matching this cache's prefix pattern")
        void clearUsesPrefixPattern() {
            // The glob 'session.*' is what stops one cache's clear() from
            // wiping every other cache sharing the Redis instance.
            when(redisTemplate.keys("session.*"))
                    .thenReturn(Set.<Object>of("session.k1", "session.k2"));

            store().clear();

            verify(redisTemplate).delete(Set.<Object>of("session.k1", "session.k2"));
        }
    }

    @Nested
    @DisplayName("compare-and-remove")
    class CompareAndRemove {

        @Test
        @DisplayName("removes and reports true when the current value matches")
        void matchingValue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("session.k1")).thenReturn("v1");
            when(redisTemplate.delete("session.k1")).thenReturn(true);

            assertThat(store().remove("k1", "v1")).isTrue();
        }

        @Test
        @DisplayName("a mismatched current value deletes nothing and reports false")
        void mismatchedValue() {
            // NOTE: possible defect — unlike the interface's "atomically"
            // promise, this is a read-then-delete: another writer can slip in
            // between get and delete and its fresh value gets removed anyway.
            // Only the single-process semantics are pinned here.
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("session.k1")).thenReturn("other");

            assertThat(store().remove("k1", "v1")).isFalse();
            verify(redisTemplate, never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("with no RedisTemplate wired (Redis not configured)")
    class NullTemplate {

        private final RedisStore<String, String> store = new RedisStore<>(null, "session", 5);

        @Test
        @DisplayName("every operation is a safe no-op instead of an NPE")
        void allOperationsAreNoOps() {
            // Services enable the cache module without Redis on the classpath
            // config; the store must behave like an always-empty cache.
            assertThatCode(() -> store.put("k1", "v1")).doesNotThrowAnyException();
            assertThat(store.get("k1")).isEmpty();
            assertThat(store.containsKey("k1")).isFalse();
            assertThatCode(() -> store.remove("k1")).doesNotThrowAnyException();
            assertThat(store.remove("k1", "v1")).isFalse();
            assertThatCode(store::clear).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("when Redis throws")
    class RedisFailures {

        @Test
        @DisplayName("a failing put is swallowed, not propagated")
        void putSwallowed() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            doThrow(new IllegalStateException("connection refused"))
                    .when(valueOps).set(any(), any(), anyLong(), any());

            assertThatCode(() -> store().put("k1", "v1")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a failing get degrades to a cache miss")
        void getDegradesToMiss() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(any())).thenThrow(new IllegalStateException("connection refused"));

            assertThat(store().get("k1")).isEmpty();
        }

        @Test
        @DisplayName("a failing containsKey reports false")
        void containsKeyDegradesToFalse() {
            when(redisTemplate.hasKey(any())).thenThrow(new IllegalStateException("connection refused"));

            assertThat(store().containsKey("k1")).isFalse();
        }

        @Test
        @DisplayName("failing removes and clear are swallowed; conditional remove reports false")
        void removesSwallowed() {
            when(redisTemplate.delete(anyString())).thenThrow(new IllegalStateException("connection refused"));
            assertThatCode(() -> store().remove("k1")).doesNotThrowAnyException();

            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(any())).thenThrow(new IllegalStateException("connection refused"));
            assertThat(store().remove("k1", "v1")).isFalse();

            when(redisTemplate.keys(any())).thenThrow(new IllegalStateException("connection refused"));
            assertThatCode(() -> store().clear()).doesNotThrowAnyException();
        }
    }
}
