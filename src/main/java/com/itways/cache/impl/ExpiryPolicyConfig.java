package com.itways.cache.impl;

import org.ehcache.expiry.ExpiryPolicy;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Custom expiry policy that allows configuring whether the TTL is reset on
 * update.
 */
public class ExpiryPolicyConfig<K, V> implements ExpiryPolicy<K, V> {

    private final Duration ttl;
    private final boolean resetOnUpdate;

    public ExpiryPolicyConfig(Duration ttl, boolean resetOnUpdate) {
        this.ttl = ttl;
        this.resetOnUpdate = resetOnUpdate;
    }

    @Override
    public Duration getExpiryForCreation(K key, V value) {
        return ttl;
    }

    @Override
    public Duration getExpiryForAccess(K key, Supplier<? extends V> value) {
        return null; // Access does not change expiry
    }

    @Override
    public Duration getExpiryForUpdate(K key, Supplier<? extends V> oldValue, V newValue) {
        return resetOnUpdate ? ttl : null;
    }
}
