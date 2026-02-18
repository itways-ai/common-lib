# Common Library (common-lib)

## 📌 Overview

The **Common Library** serves as the foundational core for the AI Assistant platform's microservices architecture. It provides a standardized set of reusable utilities, security protocols, caching mechanisms, messaging patterns, and configuration management to ensure consistency, accelerate development, and enforce best practices across all services.

This library encapsulates cross-cutting concerns such as:
- **Authentication & Authorization** (JWT, RSA Encryption)
- **Caching Abstractions** (Ehcache, InMemory)
- **Inter-service Communication** (RabbitMQ Messaging)
- **API Standards** (Unified Response, Pagination, Error Handling)
- **Utility Services** (Encryption, Date/Time, Templating)

---

## 🚀 Installation

To use `common-lib` in your Spring Boot application, add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.itways</groupId>
    <artifactId>common-lib</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## ⚙️ Configuration & Features

The library uses a modular **"Enable"** pattern. You can activate specific features by annotating your main application class or configuration classes.

### 1. Core Utilities & API Standards
Enabled by: `@EnableCommon`

Provides global exception handling (`@ControllerAdvice`), standard API response wrappers (`ApiResponse<T>`), and common DTOs.

**Usage:**
```java
@EnableCommon
@SpringBootApplication
public class MyServiceApplication { ... }
```

**Key Classes:**
- `ApiResponse<T>`: Standard wrapper for all REST API responses.
- `PageResponse<T>`: Standard pagination wrapper.
- `GlobalExceptionHandler`: Centralized error handling.

---

### 2. Security & JWT
Enabled by: `@EnableCustomSecurity`

Provides JWT generation, validation, and security context management. It standardizes how services authenticate requests and manage user sessions.

**Configuration (`application.properties`):**
```properties
# RSA Keys for JWT Signing (Base64 Encoded)
jwt.rsa.private-key=MIIEvQIBADANBgkqhkiG...
jwt.rsa.public-key=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...

# Token Expiration
jwt.access-expiration=3600000       # 1 hour (in ms)
jwt.refresh-expiration=604800000    # 7 days (in ms)
```

**Key Components:**
- `JwtTokenProvider`: Generates and validates Access and Refresh tokens.
- `SecurityUtils`: Helper to access the current authenticated user's context (e.g., User ID, Roles).

---

### 3. RSA Encryption Service
Enabled by: `@EnableEncryption`

Provides a robust RSA encryption service for securing sensitive data (PII, Secrets) at the application level. Supports chunked encryption for large payloads.

**Configuration:**
```properties
# RSA Keys for Data Encryption (Base64 Encoded)
rsa.private-key=MIIEvQIBADANBgkqhki...
rsa.public-key=MIIBIjANBgkqhkiG9w0B...
```

**Usage:**
```java
@Autowired
private EncryptionService encryptionService;

public void secureData() {
    String sensitive = "my-secret-data";
    String encrypted = encryptionService.encrypt(sensitive);
    String decrypted = encryptionService.decrypt(encrypted);
}
```

---

### 4. Caching
Enabled by: `@EnableCache`

Provides an abstraction over caching providers (currently supports **Ehcache** and **InMemory**). It allows for easy configuration of TTL (Time-To-Live) and Heap Size per cache or globally.

**Configuration:**
```properties
# Cache Provider (options: ehcache, inmemory)
itways.cache.provider=ehcache

# Default Cache Settings
itways.cache.ehcache.heapSum=1000
itways.cache.ehcache.ttlMinutes=10  # Default TTL
itways.cache.ehcache.resetTtlOnUpdate=false

# Specific Cache Configuration (e.g., for 'otp' cache)
itways.cache.caches.otp.ttlMinutes=5
itways.cache.caches.otp.heapSize=500
```

**Usage:**
Standard Spring Cache annotations work seamlessly.
```java
@Cacheable(value = "users", key = "#userId")
public User getUser(String userId) { ... }
```

---

### 5. Messaging & Notifications
Enabled by: `@EnableNotifications`

Configures the RabbitMQ infrastructure for the notification system. Sets up the necessary Exchanges, Queues, and Bindings for sending notifications (Email, SMS, etc.).

**Configuration:**
```properties
# RabbitMQ Connection
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}
spring.rabbitmq.username=${RABBITMQ_USERNAME:guest}
spring.rabbitmq.password=${RABBITMQ_PASSWORD:guest}
```

**Key Components:**
- `NotificationPublisher`: Service to publish `NotificationRequest` events to the message queue.
- `NotificationRequest`: DTO defining the structure of a notification event.

---

### 6. Templating
Enabled by: `@EnableFreeMarker`

Configures FreeMarker for email template generation.

---

## 🛠️ Usage Example

Here is a typical `SpringBootApplication` setup using `common-lib`:

```java
package com.itways.myservice;

import com.itways.annotation.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableCommon             // Core utils & Error handling
@EnableCustomSecurity     // JWT & Security Context
@EnableEncryption         // RSA Encryption Service
@EnableCache              // Caching Support
@EnableNotifications      // RabbitMQ Messaging
public class MyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyServiceApplication.class, args);
    }
}
```

## 🏗️ Project Structure

```
com.itways
├── annotation      // Enable annotations (@EnableCommon, etc.)
├── cache           // Caching implementation & config
├── common          // Core DTOs, Exceptions, Restrictions
├── encryption      // RSA Encryption logic
├── freemarker      // Templating configuration
├── notification    // Notification DTOs & Publishers
└── security        // JWT, Security Context, Authentication
```

## 📝 License

Copyright © 2024 Integral Ways. All rights reserved.
