package com.itways.cache.impl;

import com.itways.cache.CacheSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemoryStore is the in-process fallback CacheStore — the store the platform
 * quietly runs on when neither Redis nor Ehcache is configured. It does its own
 * TTL bookkeeping lazily on read, and its compare-and-remove is what makes
 * one-shot tokens (MFA replay protection) single-use, so both behaviors are
 * pinned here.
 *
 * Entry timestamps come from System.currentTimeMillis() inline — there is no
 * clock to fake. Instead of sleeping, the expiry tests use a negative
 * ttlMinutes so every entry is already expired the moment put() returns:
 * deterministic and instant. (Also worth knowing, though not asserted:
 * CacheSettings.heapSize is ignored by this implementation — the map is
 * unbounded.)
 */
@DisplayName("MemoryStore")
class MemoryStoreTest {

    /** Entries written under this config are expired before put() returns. */
    private static final CacheSettings EXPIRED_ON_ARRIVAL =
            CacheSettings.builder().ttlMinutes(-1).build();

    private MemoryStore<String, String> freshStore() {
        return new MemoryStore<>(CacheSettings.defaultConfig()); // 10-minute TTL
    }

    private MemoryStore<String, String> expiredStore() {
        MemoryStore<String, String> store = new MemoryStore<>(EXPIRED_ON_ARRIVAL);
        store.put("k1", "v1");
        return store;
    }

    @Nested
    @DisplayName("while entries are live")
    class LiveEntries {

        @Test
        @DisplayName("put/get round-trips the stored value")
        void putGetRoundTrip() {
            MemoryStore<String, String> store = freshStore();
            store.put("k1", "v1");

            assertThat(store.get("k1")).contains("v1");
            assertThat(store.containsKey("k1")).isTrue();
        }

        @Test
        @DisplayName("an absent key reads as empty, not null and not an exception")
        void absentKey() {
            MemoryStore<String, String> store = freshStore();

            assertThat(store.get("missing")).isEmpty();
            assertThat(store.containsKey("missing")).isFalse();
        }

        @Test
        @DisplayName("put on an existing key overwrites the value")
        void overwrite() {
            MemoryStore<String, String> store = freshStore();
            store.put("k1", "v1");
            store.put("k1", "v2");

            assertThat(store.get("k1")).contains("v2");
        }

        @Test
        @DisplayName("remove(key) deletes unconditionally; clear empties the store")
        void removeAndClear() {
            MemoryStore<String, String> store = freshStore();
            store.put("k1", "v1");
            store.put("k2", "v2");

            store.remove("k1");
            assertThat(store.get("k1")).isEmpty();
            assertThat(store.get("k2")).contains("v2");

            store.clear();
            assertThat(store.get("k2")).isEmpty();
        }
    }

    @Nested
    @DisplayName("once entries have expired")
    class ExpiredEntries {

        @Test
        @DisplayName("get on an expired entry is empty — expiry is enforced on read")
        void expiredGet() {
            // Cleanup is lazy: nothing evicts in the background, so the read
            // path itself must refuse to serve stale values.
            assertThat(expiredStore().get("k1")).isEmpty();
        }

        @Test
        @DisplayName("containsKey reports false for an expired entry")
        void expiredContainsKey() {
            // containsKey delegates to get(), so an expired-but-still-mapped
            // entry must not read as present.
            assertThat(expiredStore().containsKey("k1")).isFalse();
        }

        @Test
        @DisplayName("compare-and-remove treats an expired entry as absent and reports false")
        void expiredConditionalRemove() {
            // Even a matching value must not count as a successful removal once
            // the entry is dead — a replay token past its TTL was never
            // "consumed", it simply no longer exists.
            assertThat(expiredStore().remove("k1", "v1")).isFalse();
        }
    }

    @Nested
    @DisplayName("compare-and-remove semantics")
    class CompareAndRemove {

        @Test
        @DisplayName("removes and reports true only when the stored value matches")
        void matchingValue() {
            MemoryStore<String, String> store = freshStore();
            store.put("k1", "v1");

            assertThat(store.remove("k1", "v1")).isTrue();
            assertThat(store.get("k1")).isEmpty();
        }

        @Test
        @DisplayName("a mismatched value removes nothing and reports false")
        void mismatchedValue() {
            // The single-use-token guarantee: whoever holds the wrong value
            // must not be able to evict the right one.
            MemoryStore<String, String> store = freshStore();
            store.put("k1", "v1");

            assertThat(store.remove("k1", "other")).isFalse();
            assertThat(store.get("k1")).contains("v1");
        }

        @Test
        @DisplayName("an absent key reports false rather than throwing")
        void absentKey() {
            assertThat(freshStore().remove("missing", "v1")).isFalse();
        }
    }
}
