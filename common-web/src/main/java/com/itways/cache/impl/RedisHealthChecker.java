package com.itways.cache.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RedisHealthChecker {
    private final RedisConnectionFactory redisConnectionFactory;

    public RedisHealthChecker(ObjectProvider<RedisConnectionFactory> redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory.getIfAvailable();
    }

    public boolean isAvailable() {
        if(redisConnectionFactory == null) return false;
        try(var connection = redisConnectionFactory.getConnection()) {
            return "PONG".equals(connection.ping());
        } catch(Exception ex) {
            log.warn("Redis is not reachable. Falling back to local cache.");
            return false;
        }
    }
}
