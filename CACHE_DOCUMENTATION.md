# Caching Mechanism Documentation

This document explains the caching mechanism provided by `common-lib` and demonstrates its usage with an example from `auth-service`.

## Overview

The `common-lib` provides a flexible, configuration-driven caching abstraction. It supports different providers (currently **Ehcache** vs **InMemory**) and allows declaring caches via application properties.

### Key Components

1.  **`CacheStore<K, V>`**: The core interface for cache operations (`put`, `get`, `remove`, `containsKey`, `clear`).
2.  **`CacheStoreFactory`**: The interface for creating or retrieving cache instances.
3.  **`@EnableItWaysCache`**: Annotation to enable the caching infrastructure in a Spring Boot application.
4.  **`CacheConfig`**: Configuration class for cache properties (TTL, heap size).

## Configuration

You can configure the caching provider and specific caches in your `application.properties` or `application.yml`.

### Global Configuration

```properties
# Select the provider (ehcache, inmemory)
itways.cache.provider=ehcache

# Default settings for Ehcache (if not overridden per cache)
itways.cache.ehcache.heap-size=1000
itways.cache.ehcache.ttl-minutes=10
```

### Specific Cache Configuration

To create a named cache with specific settings:

```properties
# Define a cache named 'otp-transactions'
itways.cache.caches.otp-transactions.ttl-minutes=5
itways.cache.caches.otp-transactions.heap-size=1000

# Define another cache
itways.cache.caches.user-sessions.ttl-minutes=30
```

## Usage

### 1. Enable Caching

Add `@EnableItWaysCache` to your main application class or a configuration class.

```java
@Configuration
@EnableItWaysCache
public class AppConfig {
    // ...
}
```

### 2. Inject Factory and Use Cache

Inject `CacheStoreFactory` to retrieve your configured cache.

```java
@Service
public class MyService {

    private final CacheStore<String, String> myCache;

    public MyService(CacheStoreFactory factory) {
        // Retrieve the cache configured in properties as 'my-cache-name'
        this.myCache = factory.getCache("my-cache-name");
    }

    public void saveData(String key, String value) {
        myCache.put(key, value);
    }
    
    public Optional<String> getData(String key) {
        return myCache.get(key);
    }
}
```

## Example: Auth Service (OTP Transactions)

Here is a real-world example of how the `auth-service` uses this mechanism for storing OTP transactions.

### Configuration (`auth-service/src/main/resources/application.properties`)

```properties
# Configure provider
itways.cache.provider=ehcache

# Define 'otp-transactions' cache with 5 minutes TTL
itways.cache.caches.otp-transactions.ttl-minutes=5
itways.cache.caches.otp-transactions.heap-size=1000
```

### Implementation (`OtpTransactionStoreImpl.java`)

```java
@Service
public class OtpTransactionStoreImpl implements OtpTransactionStore {

    private final CacheStore<String, String> transactionCache;
    private final ObjectMapper objectMapper;

    public OtpTransactionStoreImpl(CacheStoreFactory factory, ObjectMapper objectMapper) {
        // Retrieve the 'otp-transactions' cache defined in properties
        this.transactionCache = factory.getCache("otp-transactions");
        this.objectMapper = objectMapper;
    }

    @Override
    public void storeTransaction(String transactionRef, OtpTransaction transaction, long ttlSeconds) {
        // ... serialization logic ...
        transactionCache.put(transactionRef, json, ttlSeconds);
    }

    @Override
    public Optional<OtpTransaction> getTransaction(String transactionRef) {
        Optional<String> jsonOpt = transactionCache.get(transactionRef);
        // ... deserialization logic ...
        return ...;
    }
}
```

This approach allows the `auth-service` to manage cache settings (like TTL) entirely through configuration without changing the code.
