package com.itways.cache.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RedisHealthChecker is the gate the hybrid factory consults before handing a
 * service a Redis-backed cache. Its one rule: only an actual PONG counts as
 * healthy — a missing factory bean, a connect failure, or a strange reply must
 * all read as "unavailable" so the factory falls back to the local cache
 * instead of exploding at cache-creation time.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisHealthChecker")
class RedisHealthCheckerTest {

    @Mock
    private ObjectProvider<RedisConnectionFactory> factoryProvider;

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    private RedisHealthChecker checkerWithFactory() {
        when(factoryProvider.getIfAvailable()).thenReturn(connectionFactory);
        return new RedisHealthChecker(factoryProvider);
    }

    @Test
    @DisplayName("a PONG reply reports available — and the probe connection is closed")
    void pongIsAvailable() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        assertThat(checkerWithFactory().isAvailable()).isTrue();
        // The probe runs on every hybrid-factory decision; leaking one
        // connection per cache lookup would drain the pool.
        verify(connection).close();
    }

    @Test
    @DisplayName("anything other than a PONG reply reports unavailable")
    void nonPongIsUnavailable() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn(null);

        assertThat(checkerWithFactory().isAvailable()).isFalse();
        verify(connection).close();
    }

    @Test
    @DisplayName("no RedisConnectionFactory bean at all reports unavailable without probing")
    void nullFactoryIsUnavailable() {
        // The provider is optional by design: services that never configure
        // Redis still boot with the cache module enabled.
        when(factoryProvider.getIfAvailable()).thenReturn(null);
        RedisHealthChecker checker = new RedisHealthChecker(factoryProvider);

        assertThat(checker.isAvailable()).isFalse();
        verifyNoInteractions(connectionFactory);
    }

    @Test
    @DisplayName("a connect failure reports unavailable instead of propagating")
    void connectFailureIsUnavailable() {
        when(connectionFactory.getConnection())
                .thenThrow(new IllegalStateException("connection refused"));

        assertThat(checkerWithFactory().isAvailable()).isFalse();
    }

    @Test
    @DisplayName("a ping failure reports unavailable and still closes the connection")
    void pingFailureIsUnavailable() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenThrow(new IllegalStateException("broken pipe"));

        assertThat(checkerWithFactory().isAvailable()).isFalse();
        verify(connection).close();
    }
}
