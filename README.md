# Common Library

The Common Library is the specialized core shared across the AI Assistant platform. It provides a foundational set of utilities, security protocols, messaging patterns, and standardized response models to ensure consistency and accelerate microservice development.

## 🚀 Key Features

- **Advanced Security**: 
  - Centralized JWT generation and validation via `JwtTokenProvider`.
  - Secure OTP (One-Time Password) management and lifecycle.
  - Reusable security filters and principal resolvers.
- **Messaging & Notifications**:
  - Abstractions for RabbitMQ integration (`EnableNotificationMessaging`).
  - Standardized DTOs for platform-wide events.
- **Robust Error Handling**:
  - Global `@ControllerAdvice` for consistent API error responses.
  - Specialized exception hierarchy (e.g., `PlatformException`, `AuthException`).
- **Dynamic Templating**: 
  - Integrated FreeMarker support for email and document generation.
- **Standardized API Contracts**: 
  - Unified `ApiResponse<T>` wrapper for all REST endpoints.
  - Shared pagination and sorting DTOs.

## 🛠 Installation

Add the library as a dependency in your microservice's `pom.xml`:

```xml
<dependency>
    <groupId>com.itways</groupId>
    <artifactId>common-lib</artifactId>
    <version>1.0.0</version>
</dependency>
```

## ⚙️ Configuration

The library uses a modular "Enable" pattern. You can pick and choose the features to activate in your Spring Boot application:

```java
@SpringBootApplication
@EnableCommon             // Activates shared DTOs, Handlers, and API Responders
@EnableCustomSecurity     // Activates JWT and OTP Security infrastructure
@EnableFreeMarker         // Activates document templating support
@EnableNotificationMessaging // Activates RabbitMQ integration
public class YourServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourServiceApplication.class, args);
    }
}
```

## 📖 Main Utilities

### 1. Unified Response Model
Always wrap your API results for consistent client processing.

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable Long id) {
    UserDto user = userService.getById(id);
    return ResponseEntity.ok(ApiResponse.success(user));
}
```

### 2. Security Utilities
Access the authenticated user context anywhere in your logic.

```java
@Autowired
private SecurityUtils securityUtils;

public void processAction() {
    String currentUserId = securityUtils.getCurrentUserId();
    // ...
}
```

### 3. JWT Management
Manual token generation for specialized flows (e.g., Auth Service).

```java
@Autowired
private JwtTokenProvider tokenProvider;

public String createToken(Authentication auth) {
    return tokenProvider.generateAccessToken(auth);
}
```

## 🧩 Module Breakdown

| Module | Annotation | Description |
|--------|------------|-------------|
| **Core** | `@EnableCommon` | Global exception handlers, Jackson config, and basic DTOs. |
| **Security** | `@EnableCustomSecurity` | JWT Provider, OTP Service, and Security filters. |
| **Templating** | `@EnableFreeMarker` | Configuration for FreeMarker engines. |
| **Messaging** | `@EnableNotificationMessaging` | RabbitMQ auto-configuration for event-driven flows. |

## 📝 License

Copyright © 2024 ITWays. All rights reserved.
