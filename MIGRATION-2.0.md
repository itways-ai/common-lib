# Migrating to common-lib 2.0.0

This guide moves a consumer from `com.itways:common-lib:1.0.13` to the 2.0.0 reactor.
Everything said about a consumer was read from that repository's `pom.xml` and sources,
and everything said about the library from the committed common-lib code (branch
`feature/workspace-enhancements`, commits listed at the end).

Two things change for a service as soon as it moves, before it adopts any helper:

- **`@EnableCustomSecurity` registers a `clientIpResolver` bean.** Its constructor fails
  the startup when `TRUSTED_PROXIES` names a host instead of an IP literal or CIDR range,
  and a service that already has a bean named `clientIpResolver` (account-service) does
  not start until it deletes its own (section 1, section 4).
- **`@EnableCommon` registers the request-id filter.** Every response carries
  `X-Request-Id`, and error bodies carry `reference` (section 5). Opt out with
  `itways.request-id.enabled=false`.

Already on 2.0.0? Section 7 lists what 2.1.0 changes; on 2.1.0, section 8 lists 2.2.0;
on 2.2.0, section 9 lists 2.3.0.

Order of work for one consumer:

1. the pom (section 2);
2. delete the local copies that 2.0.0 replaces, starting with the ones that clash
   (section 4);
3. properties (section 3);
4. request correlation (section 5);
5. build and verify (section 6).

## 1. What changed in 2.0.0

### One reactor, five modules

`common-lib/pom.xml` (`com.itways:common-lib-reactor:2.0.0`) only aggregates. All five
modules share version 2.0.0.

| Module | Coordinates | Contents |
| --- | --- | --- |
| `platform-bom` | `com.itways:platform-bom:2.0.0` (pom) | Versions of the in-house libraries |
| `platform-parent` | `com.itways:platform-parent:2.0.0` (pom) | Parent of every service and library |
| `common-core` | `com.itways:common-core:2.0.0` | Framework-free: `security.core`, `contracts.*`, `common.response`, `common.constants`, `common.exception`, `common.net`, `common.util`. Depends on jjwt (api compile, impl + jackson runtime) and `jackson-annotations`. `swagger-annotations-jakarta` is optional (not transitive). |
| `common-web` | `com.itways:common-web:2.0.0` | The Spring MVC side (security, scope, cache, encryption, error handling, OpenAPI, `web.client`, `web.net`, the opt-in Feign / FreeMarker / JPA integrations). Depends on `common-core`. |
| `common-messaging` | `com.itways:common-messaging:2.0.0` | RabbitMQ: JSON conversion, publisher confirms and returns, activity and notification publishers with their DTOs, the activity outbox, `DeadLetterQueueGauge`. Depends on `common-core`. |

`common-web` and `common-messaging` do not depend on each other.

### platform-parent 2.0.0

Read from `platform-parent/pom.xml`:

- **Parent:** `org.springframework.boot:spring-boot-starter-parent:3.2.2`.
- **Properties:** `java.version=21`, `project.build.sourceEncoding=UTF-8`, and
  `testcontainers.version=1.21.4`. Boot 3.2.2 would give 1.19.3, whose Docker API
  current engines refuse. Boot honours the property.
- **BOMs it imports, in this order:**
  1. `io.github.resilience4j:resilience4j-bom:2.2.0`. It comes first so that Spring
     Cloud's older resilience4j pins lose.
  2. `org.springframework.cloud:spring-cloud-dependencies:2023.0.0`
  3. `org.testcontainers:testcontainers-bom:1.21.4`
  4. `software.amazon.awssdk:bom:2.55.6`
  5. `com.itways:platform-bom:2.0.0`
- **Managed versions:**

  | Artifact | Version |
  | --- | --- |
  | `jjwt-api`, `jjwt-impl`, `jjwt-jackson` | 0.12.3 |
  | `mapstruct`, `mapstruct-processor` | 1.5.5.Final |
  | `lombok` | 1.18.30 |
  | `springdoc-openapi-starter-webmvc-ui` | 2.3.0 |
  | `archunit-junit5` | 1.5.1 |
  | `resilience4j-spring-boot3` | 2.2.0 |

- **Plugins every child runs:**
  - `jacoco-maven-plugin` 0.8.13: `prepare-agent`, plus `report` in the `test` phase.
  - `maven-surefire-plugin`: Boot's version, 3.1.2.
  - `maven-failsafe-plugin`: Boot's version, 3.1.2, bound to `integration-test` and
    `verify`.
  - `maven-source-plugin` 3.3.0 (`attach-sources`): every build now also produces a
    `-sources.jar`.
- **Configured but not activated** (a child must declare the plugin to use it):
  - `spring-boot-maven-plugin`, with Lombok excluded from the executable jar.
  - `spotless-maven-plugin` 2.46.1, bound to no phase. Run it with
    `mvn spotless:apply`.

### platform-bom 2.0.0

| Artifact | Version |
| --- | --- |
| `com.itways:common-core`, `common-web`, `common-messaging` | 2.0.0 (`${platform.version}`, a literal property kept equal to the reactor version) |
| `com.itways.assistant:ai-engine-sdk` | 1.2.0 |
| `com.itways.assistant:file-storage-sdk` | 2.0.1 |
| `com.itways.assistant:journey-model` | 1.0.18 |
| `com.itways.assistant:journey-engine-sdk` | 1.0.18 |

`ai-engine-sdk/pom.xml` still says 1.1.0 (HEAD `d90d522`), so 1.2.0 has to be released
before a consumer relies on the BOM for it (section 2d).

### Coordinates

| 1.0.13 | 2.0.0 |
| --- | --- |
| `com.itways:common-lib:1.0.13` | `com.itways:common-web` and/or `com.itways:common-messaging` (and `common-core` through them), version from `platform-bom` |
| `com.itways:common-lib:1.0.13`, classifier `security-core`, `<exclusions>*:*</exclusions>` | `com.itways:common-core`, no exclusions |

Package names did not change: every `com.itways.*` class has the same name as in 1.0.13.
Only the jar that holds it changed. Two packages span two jars, which is fine on a
classpath:

- `com.itways.annotation`: `EnableActivity` and `EnableNotifications` are in
  common-messaging; the other `@Enable*` annotations are in common-web.
- `com.itways.common`: `response`, `constants`, `exception`, `net` and `util` are in
  common-core; `config` and `handler` are in common-web.

### Removed

- **The `security-core` classifier jar.** `common-core` is that jar, complete.
- **The jar's `application.properties` and `banner.txt`.** See section 3.
- **Component scans.** `@ComponentScan("com.itways.common")` in `CommonConfig` and
  `@ComponentScan("com.itways.security")` in `SecurityConfig` are replaced by explicit
  `@Import` lists that keep the bean names (`EnableAnnotationsBeanNamesTest`).
- **`RefGenerator` (bean `refGenerator`) and `RestTemplateConfig` (bean
  `restTemplate`).** Both were unused:
  - no consumer references `RefGenerator`;
  - speech-service's `RestTemplate` injection points (`TelegramMessageSender`,
    `TelegramFileDownloader`, `TwilioMediaDownloader`) are all
    `@Qualifier("providerRestTemplate")`;
  - journey-engine-sdk's `ApiCallStepHandler` builds its own `RestTemplate`, and
    ai-engine-sdk's agents receive theirs as a constructor argument.

  A service that needs a `RestTemplate` declares its own.
- **Kept on purpose:** `RsaService` (`@EnableEncryption`, used by account and auth).

### Transitive dependencies that moved

| Dependency | From common-lib 1.0.13 | From 2.0.0 |
| --- | --- | --- |
| `spring-boot-starter-freemarker` | compile | optional in common-web: **no longer transitive** |
| `spring-boot-starter-amqp` | compile | compile in common-messaging only |
| `org.apache.httpcomponents.client5:httpclient5` | none | optional in common-web (for `web.net`) |
| `spring-web`, `spring-webmvc`, `jakarta.validation-api` | through the starters | declared by common-web |

Everything else 1.0.13 brought still comes with common-web:

- security, cache and data-redis starters;
- springdoc 2.3.0;
- jjwt;
- jackson-databind and jsr310;
- caffeine;
- ehcache 3.10.8.

common-messaging brings amqp, jackson-databind and jsr310.

### Added in 2.0.0 (ARC-11)

Shared versions of the helpers the services had copied from one another (README, "Shared
helpers"):

- `InternalEndpointGuard` / `@EnableInternalEndpointGuard`
- `Sessions`
- `ClientIp` and `ClientIpResolver`
- the overridable `GlobalExceptionHandler`
- `ServiceCalls`
- `MailSecretsConfig` / `@EnableMailSecrets`
- `DeadLetterQueueGauge`
- `web.net.*`

They are opt-in, with one part that applies as soon as a service moves:
`@EnableCustomSecurity` now also registers two beans.

- **`clientIpResolver` (`ClientIpResolver`).** Its constructor parses
  `itways.client-ip.trusted-proxies` (default `TRUSTED_PROXIES`) at startup and fails on
  a host name. The compose file sets IP literals (`docker-compose.yml:81`).
- **`serviceCalls` (from `serviceCallsConfig`).**

**account-service's own `activity/ClientIpResolver` is also a `@Component` named
`clientIpResolver`.** Delete it in the same change that moves account to 2.0.0 (section
4). Otherwise the imported bean cannot be registered under a name that is already taken,
and bean-definition overriding is off in Spring Boot. No other consumer declares a bean
named `clientIpResolver`, `serviceCalls` or `serviceCallsConfig`.

### Added in 2.0.0 (ARC-25): request correlation

Not opt-in: it is on as soon as a service moves.

- `@EnableCommon` now also imports `RequestCorrelationConfig`, which registers the
  request-id filter (`requestIdFilter`).
- common-messaging's auto-configuration carries the id over RabbitMQ.
- `ApiResponse` gains the optional `reference`, which every error body the library
  writes fills with the request id.

Section 5 has the details and the two opt-out properties. No consumer declares a bean
named `requestCorrelationConfig`, `requestIdFilter`, `requestIdPublishing`,
`requestIdListenerAdvice` or `requestIdListenerAdviceRegistrar`.

## 2. Steps per consumer type

### 2a. Servlet services (account, auth, channels, journey, template, speech, notification)

**1. Set the parent.** Replace `spring-boot-starter-parent` with:

```xml
<parent>
    <groupId>com.itways</groupId>
    <artifactId>platform-parent</artifactId>
    <version>2.0.0</version>
    <relativePath/>
</parent>
```

**2. Keep `<groupId>` and `<version>` explicit.** Without `<version>` the service would
inherit 2.0.0. Without `<groupId>` it would inherit `com.itways`. Whether to inherit is
each repository's choice; this guide does not decide it. Today's coordinates:

- `com.itways:account-service:1.0.0`
- `com.itways:auth-service:1.0.0`
- **`com.itways.assistant`**`:channels-service:1.0.0`
- `com.itways:journey-service:1.0.0`
- `com.itways:template-service:1.0.0`
- `com.itways:`**`assistant-service`**`:1.0.0`: this is speech-service.
- `com.itways:notification-service:1.0.0`

The contract tests of account, channels, speech and template look up
`com.itways:journey-service:+:stubs`, so journey-service must keep `com.itways`.

**3. Replace the dependency.** Remove `com.itways:common-lib:1.0.13` and add, without
versions:

```xml
<dependency>
    <groupId>com.itways</groupId>
    <artifactId>common-web</artifactId>
</dependency>
<dependency>
    <groupId>com.itways</groupId>
    <artifactId>common-messaging</artifactId>
</dependency>
```

**4. Delete what the parent now provides:**

| Service | Delete properties | Delete from `<dependencyManagement>` | Drop `<version>` from | Delete plugins | Keep |
| --- | --- | --- | --- | --- | --- |
| account | `java.version`, `spring-cloud.version`, `testcontainers.version` | `spring-cloud-dependencies` import | `archunit-junit5` (1.5.1) | `jacoco-maven-plugin` 0.8.13 with its executions; `maven-failsafe-plugin` with its executions | `spring-boot-maven-plugin`; stub runner with its jna exclusions |
| auth | `java.version`, `testcontainers.version` | (none) | `file-storage-sdk` (2.0.0 → BOM 2.0.1), `springdoc-openapi-starter-webmvc-ui` (2.3.0), `archunit-junit5` (1.5.1) | jacoco; failsafe; the Lombok `<configuration>` of `spring-boot-maven-plugin` (now inherited) | `spring-boot-maven-plugin` |
| channels | `java.version`, `jjwt.version`, `testcontainers.version`, `spring-cloud.version`, `archunit.version` | `spring-cloud-dependencies` import | `jjwt-api` / `-impl` / `-jackson` (`${jjwt.version}`), `archunit-junit5` (`${archunit.version}`) | jacoco; failsafe | the jjwt dependencies: used directly by `ChannelWebhookTokenProvider` and `ChannelRuntimeService` |
| journey | `java.version`, `mapstruct.version`, `testcontainers.version`, `spring-cloud.version` | `spring-cloud-dependencies` import | `journey-model` (1.0.17 → BOM 1.0.18), `mapstruct`, `mapstruct-processor`, `archunit-junit5` | jacoco; failsafe | `spring-cloud-contract.version` (4.1.0: the version of the contract plugin, and a BOM import manages dependencies, not plugins); `spring-cloud-contract-maven-plugin`; `build-helper-maven-plugin` |
| template | `java.version`, `mapstruct.version`, `testcontainers.version`, `spring-cloud.version` | `spring-cloud-dependencies` import | `mapstruct`, `mapstruct-processor`, `archunit-junit5` | jacoco; failsafe | `spring-boot-starter-freemarker` |
| speech | `java.version`, `spring-cloud.version`, `jjwt.version`, `testcontainers.version` | `spring-cloud-dependencies` import (keep the `jna` 5.13.0 entry) | jjwt ×3 (used directly), `journey-engine-sdk` (1.0.17 → 1.0.18), `ai-engine-sdk` (1.1.0 → 1.2.0), `archunit-junit5` | jacoco; failsafe | `twilio.version`; `jsoup` 1.17.2, `pdfbox` 2.0.24, `poi-ooxml` 5.2.5 (Boot does not manage them) |
| notification | `java.version`, `testcontainers.version` | (none) | `archunit-junit5` | jacoco; failsafe | (nothing) |

Dropping a pinned in-house version moves the service to the BOM's version:

- auth: file-storage-sdk 2.0.0 → 2.0.1
- journey: journey-model 1.0.17 → 1.0.18
- speech: journey-engine-sdk 1.0.17 → 1.0.18 and ai-engine-sdk 1.1.0 → 1.2.0

Test each move with the service.

**What changes in every service's build:**

- A `-sources.jar` is attached.
- Lombok leaves the executable jar in account, channels, journey, template, speech and
  notification. auth already excluded it.
- auth and notification now import the Spring Cloud BOM too. This is harmless: neither
  declares a Spring Cloud dependency.

**5. Modules each service needs.** From `import com.itways.` in `src/main` and `src/test`:

| Service | common-core (what it uses) | common-web (what it uses) | common-messaging (what it uses) |
| --- | --- | --- | --- |
| account | `ErrorCodes`, `BusinessException`, `ApiResponse`, `PageResponse`, `contracts.account.*`, `ChannelWebhookTokenClaims`, `SecurityMessages` | `@EnableCommon` / `CustomSecurity` / `Encryption` / `Cache`, `cache.*`, `EncryptionService`, `SecurityUtils`, `ApiKeyStatusStore`, `@AccountId`, `InternalServiceToken`, both auth filters, the 401/403 handlers | `@EnableActivity`, `activity.dto.*` |
| auth | `ErrorCodes`, `BusinessException`, `ApiResponse`, `SecurityMessages` | `@EnableCommon` / `CustomSecurity` / `Encryption` / `FreeMarker`, `EncryptionService`, `TemplateRender`, `SecurityUtils`, `SessionRevocationStore`, `@AccountId`, `JwtTokenProvider`, `JwtAuthenticationFilter`, the 401/403 handlers | `@EnableNotifications`, `@EnableActivity`, `activity.dto.*`, `ActivityOutbox`, `NotificationRequest`, `NotificationPublisher` |
| channels | `BusinessException`, `PublicUrlPolicy`, `ApiResponse`, `AssistantUsage`, `contracts.channels.*` | `@EnableCommon` / `CustomSecurity` / `AssistantScope`, `ChannelSecrets`, `scope.*`, `SecurityUtils`, `@AccountId`, `InternalServiceToken`, `JwtTokenProvider`, filters, handlers | `@EnableActivity`, `activity.dto.*`, `ActivityOutbox` |
| journey | `ErrorCodes`, `BusinessException`, `PublicUrlPolicy`, `ApiResponse`, `PageResponse`, `UtcDateTimes`, `contracts.account` / `channels` / `journey` / `knowledge` | `@EnableCommon` / `CustomSecurity` / `AssistantScope` / `AccountAuditing`, `MailSecrets`, `scope.*`, `@AccountId`, `InternalServiceToken`, filters, handlers | `@EnableActivity`, `activity.dto.*`, `ActivityOutbox` |
| template | `ErrorCodes`, `BusinessException`, `ApiResponse`, `PageResponse`, `contracts.account` / `channels` / `journey` / `template` | `@EnableCommon` / `CustomSecurity` / `AssistantScope` / `AccountAuditing`, `scope.*`, `@AccountId`, `InternalServiceToken`, filters, handlers | `@EnableActivity`, `activity.dto.*`, `ActivityOutbox` |
| speech | `ErrorCodes`, `BusinessException`, `PublicUrlPolicy`, `ApiResponse`, `PageResponse`, `contracts.*` | `@EnableCommon` / `CustomSecurity` / `Cache` / `FreeMarker` / `ForwardedAuth`, `cache.*`, `ChannelSecrets`, `MailSecrets`, `ForwardedAuthorizationResolver`, `TemplateRender`, `ScopeHeaders`, `SecurityUtils`, `@AccountId`, `JwtTokenProvider`, filters | `@EnableNotifications`, `NotificationPublisher`, `notification.dto.*` |
| notification | `PublicUrlPolicy` | `MailSecrets`, `ApiResponseAccessDeniedHandler`, `ApiResponseAuthenticationEntryPoint` (no common-web `@Enable*`) | `@EnableNotifications`, `notification.dto.*` |

Every servlet service needs **common-web + common-messaging**. common-core comes
through them; declare it only if you want the direct use to be explicit.

**6. Dependencies a service now declares itself:**

| Needed for | Services | Today |
| --- | --- | --- |
| `spring-boot-starter-freemarker` (`@EnableFreeMarker`: `FreeMarkerConfig` is `@ConditionalOnClass(freemarker…)`, so without FreeMarker there is no `TemplateRender` bean) | auth, speech | **auth must add it**: `notification/NotificationServiceImpl` injects `TemplateRender`, and startup fails without the starter. speech gets it today through journey-engine-sdk (which declares the starter); declare it anyway, because `turn/IntentResolver` injects `TemplateRender`. template declares it already. |
| `spring-boot-starter-amqp` | all | Comes with common-messaging. account, auth, channels, journey, template and speech also declare it (keep or drop). notification relies on the transitive one. |
| `spring-jdbc` + `spring-tx` (`@EnableAssistantScope`, the activity outbox) | scope: channels, journey, template. Outbox: auth, channels, journey, template | Nothing to do: all have `spring-boot-starter-data-jpa`. |
| `spring-data-jpa` (`@EnableAccountAuditing`) | journey, template | Nothing to do. |
| `httpclient5` (`web.net`) | speech | Declared already (version from Boot: 5.2.3). |
| Feign (`@EnableForwardedAuth`) | speech | `spring-cloud-starter-openfeign` declared already. |
| Actuator / micrometer (`DeadLetterQueueGauge`) | account, notification | Both declare `spring-boot-starter-actuator`. |
| jjwt used directly | channels, speech, auth | channels and speech declare it (drop the version). auth's `session/RefreshTokenSigner` uses `io.jsonwebtoken` without declaring it: `jjwt-api` still comes compile-scoped through common-web. |

**7. Clean-up.** `spring.freemarker.check-template-location=false` does nothing once
FreeMarker leaves the classpath. Delete it from:

- account (`application.properties:125`)
- channels (`:86`)
- journey (`:41`)
- notification (`:64`)

template (`:13`) keeps it: it declares FreeMarker itself.

### 2b. Messaging-only worker (none today)

A service that only consumes and publishes RabbitMQ messages takes **`common-messaging`
only**, with `platform-parent` as its parent:

- **Opt-ins:** `@EnableNotifications` and/or `@EnableActivity` on the application class.
- **Always on from common-messaging:**
  - `RabbitPublishingDefaults`, an `EnvironmentPostProcessor` registered in
    common-messaging's `META-INF/spring.factories`. It turns on correlated publisher
    confirms, returns and mandatory publishing; a setting of the service's own wins.
  - `RabbitPublishingAutoConfiguration`, which logs nacks and unroutable returns
    (`itways.rabbitmq.returns.quiet-exchanges`) and carries the request id on
    published and consumed messages (section 5).
- **Activity outbox:** add `spring-boot-starter-jdbc`, which brings `spring-jdbc` and
  `spring-tx` (optional in common-messaging), plus a `DataSource`.
- **`DeadLetterQueueGauge`:** add `spring-boot-starter-actuator`.
- **What it does not get:** no servlet stack, no JWT keys, no security filters. Those
  are all common-web.

### 2c. Reactive gateway (api-gateway)

**Pom** (`api-gateway/pom.xml`):

- Parent: `spring-boot-starter-parent` 3.2.2 → `com.itways:platform-parent:2.0.0`
  (`<relativePath/>`).
- Delete properties `java.version`, `spring-cloud.version`, `common-lib.version` and
  `jjwt.version`. Keep `maven.test.skip`: the dependency plugin reads it.
- Delete the `spring-cloud-dependencies` import.
- Replace the classifier dependency and its wildcard exclusion with:

  ```xml
  <dependency>
      <groupId>com.itways</groupId>
      <artifactId>common-core</artifactId>
  </dependency>
  ```

  No exclusions are needed. common-core brings jjwt (`jjwt-api` compile, `jjwt-impl`
  and `jjwt-jackson` runtime, the scopes the gateway declares today) and
  `jackson-annotations`. Its swagger annotations are optional and do not follow.
- The three jjwt dependencies can go. Gateway main code does not import
  `io.jsonwebtoken`; `CommonLibCompatibilityTest` does, through the transitive
  `jjwt-api`. Keep `jjwt-api` without a version if you prefer to declare what the tests
  use.
- `spring-boot-maven-plugin`: its Lombok `<configuration>` is now inherited. Keep the
  plugin declaration.
- New for the gateway: JaCoCo, failsafe (it has no `*IT`) and the sources jar come from
  the parent.
- `maven-dependency-plugin`: the full jar that `CommonLibCompatibilityTest` loads is
  now `common-web`, which holds `JwtTokenProvider`, `SecurityUtils` and
  `ApiKeyProvider`:

  ```xml
  <artifactItem>
      <groupId>com.itways</groupId>
      <artifactId>common-web</artifactId>
      <!-- no <version>: the plugin takes it from dependencyManagement (platform-bom) -->
      <destFileName>common-lib.jar</destFileName>
  </artifactItem>
  ```

  Keep the output directory, `destFileName` and the surefire system property
  `commonLib.jar`, so `CommonLibSpringClasses` needs no change. It loads those three
  classes in a child class loader whose parent is the test class path. The common-core
  classes they use (`security.core`, `common.constants`, `common.exception`) now come
  from the gateway's own common-core, just as `security.core` came from the classifier
  jar before.
- `onlyTheSecurityCoreOfCommonLibIsOnTheClasspath` stays valid: common-core has no
  servlet, MVC, springdoc or AMQP class, no `SecurityUtils` or `JwtTokenProvider`, and
  no `AutoConfiguration.imports`.

**Code:**

| Local (under `src/main/java/com/itways/assistant/gateway/`) | Replace with | Notes |
| --- | --- | --- |
| `net/IpLiterals.java` | `com.itways.common.net.IpLiterals` | Identical except the package line (diffed). |
| `net/TrustedProxies.java` | `com.itways.common.net.TrustedProxies` | Identical except the package line. `net/TrustedProxiesTest` duplicates common-core's `TrustedProxiesTest`. `net/TrustedProxiesPropertyTest` checks the gateway's own `gateway.client-ip.trusted-proxies`: keep it. |
| `filter/ClientAddressFilter.resolveClient` (hop parsing and choice) | `ClientIp.hops(forwardedFor)`, or `ClientIp.resolve(forwardedFor, peer.getHostAddress(), trustedProxies)` | Same rule. The filter stays: it rewrites `X-Forwarded-For` / `X-Forwarded-Proto`. |
| `support/ErrorCodes.java` | **Partly** `com.itways.common.constants.ErrorCodes` | `UNAUTHORIZED` (`AUTH_401`), `FORBIDDEN` (`AUTH_403`) and `EXTERNAL_SERVICE_ERROR` (`SYS_002`) are equal in common-core. `NOT_FOUND` must stay `"NOT_FOUND"`: common-core's `ErrorCodes.NOT_FOUND` is `"RES_001"`. The services' real-404 code `NOT_FOUND` lives in common-web (`GlobalExceptionHandler.NOT_FOUND_CODE`). `INTERNAL_SERVER_ERROR` has no equal: core's `INTERNAL_ERROR` is `"SYS_001"`. Neither does `FRAMEWORK_ERROR_PREFIX`. Keep those three local. |
| `support/ErrorResponseWriter.java` | Keep it (reactive writer) | The timestamp can use `UtcDateTimes.format(LocalDateTime.now(ZoneOffset.UTC))`, which gives the same string. Do not serialize `ApiResponse` with the gateway's `ObjectMapper`: its `LocalDateTime` would lose the `Z`, which the services add through `TimeConfig` (common-web). |
| Request id (GW-12) | `com.itways.common.correlation.RequestIds` | See section 5. |

### 2d. SDKs (file-storage-sdk, ai-engine-sdk, journey-engine)

None of them depends on common-lib. They adopt `platform-parent` 2.0.0 as their parent.

- Keep `<groupId>com.itways.assistant</groupId>` and `<version>` explicit:
  `platform-bom` names them that way.
- Never declare `spring-boot-maven-plugin`: these are libraries.
- Build common-lib first (section 6).

| SDK | Today | Change |
| --- | --- | --- |
| **file-storage-sdk** 2.0.1 | Parent `spring-boot-starter-parent` 3.2.2. Properties `java.version`, `aws-sdk.version` (2.55.6). Imports `software.amazon.awssdk:bom`. Plugins `maven-source-plugin` 3.3.0 and `jacoco-maven-plugin` 0.8.13. | Parent → `platform-parent`. Delete both properties, the AWS BOM import (the parent imports 2.55.6) and both plugin blocks. The dependencies stay as they are, s3 exclusions included. |
| **ai-engine-sdk** (pom says 1.1.0; `platform-bom` pins **1.2.0**) | No parent. Properties `maven.compiler.source` / `target` (21), `project.build.sourceEncoding`, `spring-boot.version` (3.2.2). Hand pins: `spring-boot-starter` / `-web` / `-test` `${spring-boot.version}`, `slf4j-api` 2.0.11, `lombok` 1.18.30, `jackson-databind` 2.15.3, `httpclient5` 5.2.1, `httpcore` 4.4.16, `langchain4j-ollama` 0.31.0. Plugins: `maven-compiler-plugin` 3.11.0 (source/target 21, Lombok annotation processor path), `maven-source-plugin` 3.3.0, `maven-surefire-plugin` 3.2.5, `jacoco` 0.8.13. | Add the parent. Delete the four properties. Drop the versions of the Spring Boot starters, `slf4j-api` (Boot: 2.0.11), `lombok` (1.18.30), `jackson-databind` (2.15.3), `httpcore` (4.4.16) and `httpclient5` (Boot: **5.2.3**, a patch upgrade; keep the pin to stay on 5.2.1). Keep `langchain4j-ollama` 0.31.0. Delete all four plugin blocks: the parent sets Java 21 and runs the source plugin and JaCoCo. Lombok on the compile classpath is found by javac on JDK 21, as in every service. Surefire becomes Boot's 3.1.2, which runs JUnit 5 (the reason for the 3.2.5 pin is gone). **Release as 1.2.0** before any consumer drops its pin. |
| **journey-engine** 1.0.18 (`journey-engine-parent`, modules `journey-model`, `journey-engine-sdk`) | `journey-engine/pom.xml`: no parent; properties `maven.compiler.source` / `target`, `project.build.sourceEncoding`, `lombok.version` (1.18.30), `jackson.version` (2.15.3), `junit.version` (5.10.1), `assertj.version` (3.24.2); pluginManagement plus plugins for compiler 3.11.0 (Lombok path), source 3.3.0 and surefire 3.2.5. `journey-model`: `jackson-annotations`, `lombok`, `junit-jupiter`, `assertj-core` with those properties. `journey-engine-sdk`: property **`spring-boot.version` 3.2.0**; `spring-boot-starter` / `-web` / `-freemarker` at 3.2.0; `lombok`; `jackson-databind`; **`ai-engine-sdk` 1.1.0**; `httpclient5` 5.2.1; GraalVM JS `${graalvm.js.version}` (24.1.2); `junit` / `assertj`. | Give `journey-engine-parent` the parent `platform-parent`. Delete the six properties (Boot 3.2.2 manages lombok 1.18.30, jackson 2.15.3, junit-jupiter 5.10.1 and assertj 3.24.2: the same versions) and the compiler / source / surefire configuration. In both modules, drop the versions of the managed artifacts. In journey-engine-sdk, delete `spring-boot.version`: **Spring Boot moves 3.2.0 → 3.2.2**. Drop the `ai-engine-sdk` version (**1.1.0 → 1.2.0** from the BOM), drop `httpclient5`'s (→ 5.2.3), keep `graalvm.js.version`. The modules keep inheriting their groupId and version from `journey-engine-parent`. |

## 3. Properties

### 3.1 The deleted `application.properties`

The complete file the 1.0.13 jar shipped:

```properties
spring.output.ansi.enabled=ALWAYS

# RabbitMQ Configuration
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}
spring.rabbitmq.username=${RABBITMQ_USERNAME:guest}
spring.rabbitmq.password=${RABBITMQ_PASSWORD:guest}
```

**No service ever read it.** Spring Boot loads `classpath:/application.properties` as a
single resource (the first one on the class path, not `classpath*:`). Every servlet
service has its own `src/main/resources/application.properties`, and the service's
classes come before its jars, so the jar's file was shadowed everywhere. The gateway used
the classifier jar, which did not contain the file. Removing it changes nothing.

RabbitMQ connection keys, where each service sets them:

| Service | Keys | Where |
| --- | --- | --- |
| account | `spring.rabbitmq.host` / `port` / `username` / `password` = `${RABBITMQ_HOST:localhost}` etc. | `application.properties:32-35` |
| auth | same keys, `${SPRING_RABBITMQ_HOST:localhost}` etc. | `:84-87` |
| channels | `${RABBITMQ_*}` | `:37-40` |
| journey | `${RABBITMQ_*}` | `:10-13` |
| template | `${RABBITMQ_*}` | `:102-105` |
| speech | `${SPRING_RABBITMQ_*}` | `:77-80` |
| notification | **none**: Boot's defaults (localhost:5672, guest/guest) | only `spring.rabbitmq.listener.simple.*` |

In compose, every service that uses RabbitMQ gets `SPRING_RABBITMQ_HOST` / `PORT` /
`USERNAME` / `PASSWORD` in its own `environment:` block, from the `x-rabbitmq-env` fragment
it merges (`docker-compose.yml`; one block per service since PLT-31, which replaced the
former shared `x-common-env`). Boot binds those environment variables directly, ahead of
`application.properties`. No service sets
`spring.output.ansi.enabled`, so Boot's default (`detect`) applied before and still
applies.

**Banner.** No service has a `banner.txt` of its own, and none sets `spring.banner.*` or
`spring.main.banner-mode`. So the services did print the jar's banner. From 2.0.0 they
print Spring Boot's default banner. To change that, add `src/main/resources/banner.txt`
or set `spring.main.banner-mode=off`.

### 3.2 Library properties that still exist (defaults read from the code)

| Property | Default | Read by |
| --- | --- | --- |
| `itways.internal-token` | empty | `InternalServiceToken` (also sent by `ServiceCalls` and the Feign interceptor) |
| `itways.internal-token-enforce` | `false` | `InternalServiceToken.admits` |
| `itways.activity.outbox.enabled` | `false` | `ActivityOutboxProperties` |
| `itways.activity.outbox.table` | none (required when enabled; `[a-z_][a-z0-9_]{0,62}`) | same |
| `itways.activity.outbox.relay-enabled` | `true` | same |
| `itways.activity.outbox.poll-interval` / `batch-size` | `2s` / `100` | same |
| `itways.activity.outbox.confirm-timeout` | `10s` | same |
| `itways.activity.outbox.initial-backoff` / `max-backoff` | `1s` / `5m` | same |
| `itways.activity.outbox.retention` / `cleanup-interval` | `7d` / `1h` | same |
| `itways.activity.outbox.stats-interval` / `lag-warning` | `15s` / `5m` | same |
| `itways.cache.provider` | `ehcache` (`redis` = Redis while reachable) | `CacheProperties` |
| `itways.cache.ehcache.heap-size` / `ttl-minutes` / `reset-ttl-on-update` | `1000` / `10` / `false` | same |
| `itways.cache.redis.ttl-minutes` | `10` | same |
| `itways.cache.manager.maximum-size` / `ttl` | `10000` / `10m` | same |
| `itways.cache.caches.<name>.*` | none | per-cache `CacheSettings` |
| `itways.security.default-user.enabled` | `false` | `GeneratedUserFilter` |
| `itways.rabbitmq.returns.quiet-exchanges` | `account.events` | `RabbitPublishingAutoConfiguration` |
| `jwt.rsa.public-key` (`RSA_PUBLIC_KEY`), `jwt.rsa.private-key`, `jwt.channel-webhook.public-key` / `previous-public-key`, `jwt.encryption.key`, `jwt.access-expiration` (`3600000`), `jwt.refresh-expiration` (`604800000`), `security.session-revocation.ttl` (`2d`) | as listed | `JwtTokenProvider`, `SecurityUtils`, `SessionRevocationStore` |

### 3.3 New in 2.0.0

| Property | Default | Effect |
| --- | --- | --- |
| `itways.internal-guard.enabled` | `true` | With `@EnableInternalEndpointGuard`: `false` registers no filter. |
| `itways.internal-guard.proxy-headers` | `X-Forwarded-For,X-Forwarded-Host,Forwarded` | The headers whose presence marks a proxied request. At least one is required. |
| `itways.client-ip.trusted-proxies` | `${TRUSTED_PROXIES:127.0.0.0/8,::1/128,172.16.0.0/12}` | `ClientIpResolver` (every `@EnableCustomSecurity` service). IP literals and CIDR ranges only; a name fails the startup. |
| `itways.errors.hide-server-error-messages` | `false` | `true`: a 5xx `BusinessException` answers its own status with `Internal server error (reference R)` / `INTERNAL_SERVER_ERROR`. |
| `itways.mail-secrets.required` | `true` | With `@EnableMailSecrets`: `false` means a blank key registers no `MailSecrets` bean (with a WARN) instead of failing the start. |
| `mail.secrets.key`, `mail.secrets.previous-key` | `${MAIL_SECRETS_KEY:}`, `${MAIL_SECRETS_KEY_PREVIOUS:}` | Read by `@EnableMailSecrets`. journey and notification already set these two keys to the same variables. |
| `itways.request-id.enabled` | `true` | With `@EnableCommon` or `@EnableRequestCorrelation`: `false` registers no `requestIdFilter`, so no `X-Request-Id` response header, no `requestId` in the logging context and no `reference` in error bodies. Constant `RequestCorrelationConfig.ENABLED_PROPERTY`. |
| `itways.request-id.messaging.enabled` | `true` | `false` registers neither the `requestIdPublishing` customizer nor the `requestIdListenerAdvice` bean and its registrar. The outbox still records an event's `requestId` and sends it as the header, and `ActivityEventPublisher` still sends an event's own `requestId`. Constant `RabbitPublishingAutoConfiguration.REQUEST_ID_ENABLED_PROPERTY`. |

## 4. Local classes each service deletes

Paths are relative to each repository's `src/main/java/com/itways/assistant/<service>/`
and were checked at the commits listed at the end. auth-service's paths are after
ARC-14, journey-service's after ARC-17. Delete a class in the same change that adds its
replacement. `@EnableMailSecrets` and `@EnableInternalEndpointGuard` register beans named
`mailSecretsConfig` / `mailSecrets` and `internalEndpointGuardFilter`, the names the local
copies use.

Behaviour changes that apply to every service that adopts the helper:

- **Guard.**
  - The refusal body is always `The requested resource was not found` / `NOT_FOUND`.
  - The filter order is `HIGHEST_PRECEDENCE + 10`; it was `HIGHEST_PRECEDENCE`.
  - A response that is already committed is left alone.
- **Sessions.**
  - A session that names a `tokenType` other than `ACCESS` is refused.
  - A blank principal name is refused.
  - The JWT filter already rejects refresh tokens, so real traffic only changes for
    token types nobody mints yet. Test fixtures that set another `tokenType` change too.
- **Client IP.**
  - Every `X-Forwarded-For` line is read, not only the first.
  - A hop is normalised: quotes, brackets and `:port` are removed.
  - A hop that is not an IP literal is skipped, never returned as the client.
  - The result is cut to 64 characters.
- **ServiceCalls.**
  - Clients are built from a clone of Boot's `RestClient.Builder` (Boot's customizers
    apply) rather than `RestClient.builder()`.
  - Every call made while a request is being served carries that request's
    `Authorization` and `X-API-KEY`.
- **DLQ gauge.**
  - The series gains the tag `queue=<queue>`; metric names are unchanged.
  - A failure logs the exception's class name at DEBUG.

### account-service

> **account-service does not start on 2.0.0 until it deletes `activity/ClientIpResolver`
> in the same change.** That class is a `@Component` whose bean name is
> `clientIpResolver`, the same name as the `com.itways.security.servlet.ClientIpResolver`
> bean that `@EnableCustomSecurity` now registers. account has `@EnableCustomSecurity`
> and does not allow bean-definition overriding, so the context fails while it
> registers the second definition. Switch `ActivityRecorder` to the shared resolver and
> delete the local class (first row below) before the first 2.0.0 build.

| Local | Replace with | Notes |
| --- | --- | --- |
| `activity/ClientIpResolver.java` (`@Component` `clientIpResolver`) | `com.itways.security.servlet.ClientIpResolver`: bean `clientIpResolver` from `@EnableCustomSecurity`; `clientIp(request)` → `resolve(request)` | **Required with the move**: the bean name clashes (section 1). Replace `account.client-ip.trusted-proxies` (`application.properties:91`) with `itways.client-ip.trusted-proxies`; the same default applies without the line. Differences: see "Client IP" above. Entries must be literals; `IpAddressMatcher` accepted names. Tests: `ClientIpResolverTest.garbageInTheHeaderIsNotTreatedAsAProxy` expected `evil.example.com` and now expects the peer `172.18.0.5`. `TrustedProxiesPropertyTest` reads the old key. `AccountPostgresIT` and `AccountDeletionIT` import the local class. |
| `config/InternalEndpointGuard.java` (was `web/`, moved by ARC-17) | `@EnableInternalEndpointGuard` on `AccountServiceApplication` | The body is unchanged (account already sent the real-404 body). Order goes to +10. `AccountDeletionWebTest` and `AccountWebSecurityTest` `@Import` the local class: import `InternalEndpointGuardConfig` instead. |
| `config/AccountExceptionHandler.java` (was `web/`) | **Delete it and set `itways.errors.hide-server-error-messages=true`**, or keep an empty `AccountExceptionHandler extends GlobalExceptionHandler` overriding `hideServerErrorMessages()` to return `true` | Every handler it has is a framework one that the base now has with the same answer. `handleDataIntegrity` has the same text and code as `DataAccessExceptionHandler`. Do not keep its methods in a subclass: `handleValidation` and `handleUnexpected` would map the same exceptions as the inherited `handleValidationExceptions` / `handleGeneralException` (startup fails with "Ambiguous @ExceptionHandler method"). Its private static `error(...)` and `validationFailed(...)` clash with the base's protected hooks and do not compile. Answers that change: `MultipartException` becomes 400 `MALFORMED_REQUEST` (was 500); `AuthenticationException` becomes 401 `AUTH_401` (was 500); a 4xx `ResponseStatusException` answers with its reason (was the reason phrase); an unnamed method-validation parameter is keyed `argN` (was `null`); a 5xx `BusinessException` keeps its own status (was always 500; account throws none today: its statuses are 400, 403, 404 and 409). Tests import `GlobalExceptionHandler` + `DataAccessExceptionHandler` instead. |
| `activity/DeadLetterQueueGauge.java` | `@Bean DeadLetterQueueGauge activityDlqGauge(AmqpAdmin admin) { return new DeadLetterQueueGauge(admin, "account.activity.dlq.messages", ActivityDeadLetterConfig.DEAD_LETTER_QUEUE, "Activity events in account.activity.dlq (-1: broker unreachable)"); }` | The gauge no longer needs scheduling. Keep `@EnableScheduling` anyway: `ApiKeyAllowListSync` and `ActivityRetention` use it. Gains `queue="account.activity.dlq"`. |
| `assistants/ForwardedCaller.java` | `ServiceCalls`: in `AssistantUsageClient` and `KnowledgeIndexVisibility`, `serviceCalls.client(baseUrl)` (the same 2 s / 5 s JDK factory) | Remove `callerAndServiceHeaders` and the `serviceToken` fields. The headers are the same (token, `Authorization`, `X-API-KEY`). |
| `deletion/AccountDeletionController.validToken` | `internalServiceToken.matches(presentedToken)` | Same rule: strict, trimmed, constant-time, `false` without a configured token. |
| `config/SecurityConfig.USER_SESSION` | `.anyRequest().access(Sessions.userSession())` | Keep `FORBIDDEN_MESSAGE`. |
| `activity/ActivityRecorder.currentCredentialKind` | `Sessions.kindOf(auth)` mapped to the stored `via` values: `USER` → `"USER"`, `API_KEY` → `"API_KEY"`, `CHANNEL_WEBHOOK` → **`"WEBHOOK"`**, `NONE` → no `via` | `via` is stored in activity metadata: keep `"WEBHOOK"`. Before, any details map that was neither an API key nor a webhook gave `"USER"`. |
| (done) `spring-cloud-starter-openfeign` and `@EnableForwardedAuth` | none | Already removed in `2986eb6` (ARC-11): no Feign dependency, no Feign client. |

### auth-service

| Local | Replace with | Notes |
| --- | --- | --- |
| `audit/ClientRequestInfo.java`, the IP part (`ipAddress`, `currentIpAddress`, `isTrustedProxy`, the `IPV4`/`IPV6` patterns) | `ClientIpResolver.current().orElse(null)` / `resolve(request)` | Keep `currentUserAgent()`. Set `itways.client-ip.trusted-proxies=${TRUSTED_PROXIES:${AUTH_TRUSTED_PROXIES:127.0.0.0/8,::1/128,172.16.0.0/12}}` to keep reading the legacy variable during its window (today's `auth.client-ip.trusted-proxies`, `application.properties:179`), then remove `AuthProperties.ClientIp`. The IP feeds the per-client limiter (`throttle/ClientLimiter`) and audit rows: a junk hop no longer becomes its own bucket. Tests: `ClientRequestInfoTest`, `config/TrustedProxiesPropertyTest`, `ClientLimiterTest`, `ClientThrottleWebTest`. |
| `config/AuthExceptionHandler.java` | `class AuthExceptionHandler extends GlobalExceptionHandler`, kept `@RestControllerAdvice` and `@Order(Ordered.HIGHEST_PRECEDENCE)` | Keep the domain handlers: `TooManyAttemptsException`, `AuthStoreUnavailableException`, `NotificationDeliveryException`, `StorageUnavailableException`, `OAuthException`, `RedisConnectionFailureException`, and `DataIntegrityViolationException` if `CONFLICT` must stay. The `@Order` makes it run before `DataAccessExceptionHandler` (`HIGHEST_PRECEDENCE + 1`). Override `hideServerErrorMessages()` to return `true`, or set the property; this replaces `handleBusiness`. Override `clientErrorCode(status)` to return `HttpStatus.resolve(status.value())`'s `name()` (else `BAD_REQUEST`); this replaces `handleUnexpected`'s code. Delete the private static `error(HttpStatus, String, String)`: it clashes with the base's protected hook and does not compile. A framework handler kept for its old text must **override the base method by name**. Otherwise it maps the same exception twice and startup fails. The differences are in the next table. |
| `config/SecurityConfig.userAccessTokenOnly()` | `.access(Sessions.userSession())` on `/api/auth/me/**` and `/api/auth/admin/**` | Before, `tokenType` had to be present. Now a details map without it counts as a user. auth's JWT filter always sets it, so there is no runtime change. A blank principal is refused. |

auth's framework answers compared with the shared base. Keep auth's answer by overriding
the named base method; otherwise update the tests (and anything that reads these codes)
to the shared answer.

| Exception | auth today (message / code) | Shared base | Base method to override |
| --- | --- | --- | --- |
| `HttpMessageNotReadableException` | `Malformed or missing request body` / `BAD_REQUEST` | `Request body is missing or is not valid JSON` / `MALFORMED_REQUEST` | `handleUnreadableBody` (auth's is `handleNotReadable`) |
| `MissingServletRequestParameterException` | same text / `BAD_REQUEST` | same text / `MISSING_PARAMETER` | `handleMissingParameter` |
| `MissingServletRequestPartException` | same text / `BAD_REQUEST` | same text / `MISSING_PARAMETER` | `handleMissingPart` |
| `MethodArgumentTypeMismatchException` | `Invalid value for parameter 'x'` / `BAD_REQUEST` | `Parameter 'x' has an invalid value` / `INVALID_PARAMETER` | `handleTypeMismatch` |
| `MultipartException` | `Malformed multipart request` / `BAD_REQUEST` | `The multipart request could not be read` / `MALFORMED_REQUEST` | `handleMultipart` |
| `MaxUploadSizeExceededException` | 413 `Uploaded file is too large (maximum N MB)` / `PAYLOAD_TOO_LARGE` | 413 `The request is too large` / `PAYLOAD_TOO_LARGE` | `handleUploadTooLarge` (auth's is `handleMaxUploadSize`) |
| `HttpMediaTypeNotSupportedException` | `Unsupported content type[; expected …]` / `UNSUPPORTED_MEDIA_TYPE` | `Content type is not supported by this endpoint` / same code | `handleMediaType` (auth's is `handleMediaTypeNotSupported`) |
| `HttpRequestMethodNotSupportedException` | `HTTP method not allowed for this resource` / `METHOD_NOT_ALLOWED` | `Method X is not supported on this path` / same code | `handleMethodNotSupported` |
| `DataIntegrityViolationException` | 409 `The request conflicts with existing data` / `CONFLICT` | 409 `… ; reload and try again (reference R)` / `DATA_CONFLICT` (`DataAccessExceptionHandler`) | not in the base: keep auth's handler with `@Order(HIGHEST_PRECEDENCE)` |
| `HandlerMethodValidationException` | key = parameter name or `parameterN`, first error only | key = parameter name or `argN`, field errors as `param.field` | `handleMethodValidation` (auth's is `handleHandlerMethodValidation`) |
| `ConstraintViolationException` | key = leaf node name (`request` when none) | key = full property path | `handleConstraintViolation` |
| other framework 4xx (catch-all) | reason phrase / `HttpStatus.name()` | reason phrase (or a `ResponseStatusException`'s reason) / `HTTP_<n>` | hook `clientErrorCode` |
| unchanged | `MethodArgumentNotValidException`, `NoResourceFoundException`, `AccessDeniedException`, `AuthenticationException`, unexpected 500, 5xx `BusinessException` (with the hide flag) | same | delete auth's copies (`handleMethodArgumentNotValid`, `handleNoResource`, `handleAccessDenied`, `handleAuthentication`, `handleBusiness`, `handleUnexpected`) |

### channels-service

| Local | Replace with | Notes |
| --- | --- | --- |
| `config/InternalEndpointGuard.java` | `@EnableInternalEndpointGuard` | The proxied-probe body changes from `Not found` / `NOT_FOUND` to `The requested resource was not found` / `NOT_FOUND`. `config/InternalEndpointGuardTest` builds the local filter. Build the shared one instead: `new com.itways.security.internal.InternalEndpointGuard(serviceToken, objectMapper)` (note the argument order). It asserts status codes only. |
| `integrations/ServiceHttp.java` | In `integrations/JourneyDirectory`: `serviceCalls.builder().baseUrl(journeyServiceUrl).requestFactory(outboundRequestFactory).build()`, and remove `.headers(ServiceHttp::forwardAuthorization)` | Calls to journey-service now also forward `X-API-KEY`. |
| `config/SecurityConfig.USER_SESSION` | `Sessions.userSession()` | Keep `FORBIDDEN_MESSAGE`. |

### journey-service

| Local | Replace with | Notes |
| --- | --- | --- |
| `config/InternalEndpointGuard.java` | `@EnableInternalEndpointGuard` | The body changes from `Not found` / `RES_001` to `The requested resource was not found` / `NOT_FOUND`: message **and** code. Update `config/InternalEndpointGuardTest` as for channels. |
| `integrations/ServiceHttp.java` | `serviceCalls.client(url)` in `integrations/templates/TemplateServiceClient` and `integrations/channels/VoiceReach` (the same 2 s / 5 s); remove the `.headers(...)` forwarding | `VoiceReach`'s call to channels-service now also forwards `X-API-KEY`. `TemplateServiceClient` already forwarded both. |
| `support/Sessions.java` | `com.itways.security.servlet.Sessions` (same `isUserSession(Authentication)`) in `config/SecurityConfig` and `knowledge/KnowledgeBaseController` | Update the `support` package-info and the README, which mention it. |
| `config/MailSecretsConfig.java` | `@EnableMailSecrets` | Same keys (`application.properties:57-58`). A blank key still fails the start (`required` defaults to `true`). `JourneyPostgresIT` imports the local class. |
| (done) `support/RequestHeaders.ASSISTANT` | `ScopeHeaders.ASSISTANT` | Already done in `d057212` (ARC-17): no `RequestHeaders` class remains. |

### template-service

| Local | Replace with | Notes |
| --- | --- | --- |
| `config/InternalEndpointGuard.java` | `@EnableInternalEndpointGuard` | Body `Not found` / `RES_001` → `The requested resource was not found` / `NOT_FOUND`. Update `config/InternalEndpointGuardTest`. |
| `config/SecurityConfig.isUserSession` / `USER_SESSION` | `Sessions.isUserSession` / `Sessions.userSession()` | Tests: `TemplateWebSecurityTest`, `TemplateRenderWebTest`, `TemplateVersionsWebTest`. |
| `config/RenderLaneResolver` (own token bytes plus `InternalEndpointGuard.cameThroughProxy`) | Inject `InternalServiceToken`. When `!isConfigured()`, use `com.itways.security.internal.InternalEndpointGuard.cameThroughProxy(request) ? INTERACTIVE : RUNTIME`; otherwise `matches(request.getHeader(InternalServiceToken.HEADER)) ? RUNTIME : INTERACTIVE` | Same decision. Keep the startup WARN. `RenderLaneResolverTest`. |
| `integrations/JourneyUsageClient.callerAndServiceHeaders` and its own `RestClient.builder()` | `serviceCalls.client(journeyUrl)`; for the test constructor, `serviceCalls.client(url, Duration.ofMillis(connect), Duration.ofMillis(read))` | Same headers. The client is now a clone of Boot's builder. |

### notification-service

| Local | Replace with | Notes |
| --- | --- | --- |
| `config/MailSecretsConfig.java` | `@EnableMailSecrets` | Same keys (`application.properties:24-25`); a blank key fails the start, as today. |
| `messaging/DeadLetterQueueGauge.java` | `@Bean DeadLetterQueueGauge notificationDlqGauge(AmqpAdmin admin) { return new DeadLetterQueueGauge(admin, "notification.dlq.messages", DeadLetterConfig.DEAD_LETTER_QUEUE, "Notifications in notification.dlq (-1: broker unreachable)"); }` | The metric name is unchanged. `DeadLetterFlowIT` checks `notification_dlq_messages` as a substring, which still matches with the new `queue` tag. **Remove `@EnableScheduling`** from `NotificationApplication`: the gauge's `refresh` was its only `@Scheduled` method. |

### speech-service

| Local | Replace with | Notes |
| --- | --- | --- |
| `net/PinnedHttpClients.java`, `net/PublicOnlyDnsResolver.java`, `net/BoundedDownloads.java` | `com.itways.web.net.*` (swap the imports) | The public signatures are a superset (compared). Users: `identity/HostTokenExchanger`, `config/ProviderHttpConfig`, `config/ProviderHttpProperties`, `channel/telegram/TelegramFileDownloader`, `channel/twilio/TwilioMediaDownloader`, `knowledge/KnowledgeSourceService`. Tests: `HostTokenExchangerTest`, `KnowledgeSourceFetchTest`. `net/BoundedDownloadsTest` and `net/DnsRebindingTest` have equivalents in common-web. Behaviour: `PinnedHttpClients` now never keeps cookies, and the wait for a pooled connection is bounded by the connect timeout. |
| `chat/ChatOwner.isUserSession` | `Sessions.isUserSession` (in `ChatOwner.current` and `chat/ChatTurns`) | The blank-principal check was speech's already. New: a `tokenType` other than `ACCESS` is refused. |
| `engine/RabbitMailDeliveryAdapter` (inline `new MailSecrets(key, previousKey)`) | `@EnableMailSecrets` plus `itways.mail-secrets.required=false`. The adapter takes `ObjectProvider<MailSecrets>` (`getIfAvailable()`; null means no key) | Same keys and fallbacks. Without a key: a WARN from the library, and plain passwords are refused as today. `RabbitMailDeliveryAdapterTest` uses the `(publisher, key, previousKey)` constructor. |
| Internal guard | Optional `@EnableInternalEndpointGuard` | speech has **no** `/internal/` route any more. `DELETE /api/internal/cache/ai-configs/{accountId}` went with `CacheEvictionController` in `e973a2b` (ARC-19). No `@RestController` maps an `internal` path. The guard is defence in depth only. |

### api-gateway

See section 2c: `net/IpLiterals`, `net/TrustedProxies`, `ClientAddressFilter.resolveClient`,
`support/ErrorCodes` (partly), `support/ErrorResponseWriter`.

## 5. Request correlation (ARC-25)

One id follows a request through every service it touches. The logs of all of them can
be searched for it, and a caller can quote it from an error body.

### The rule (common-core)

`com.itways.common.correlation.RequestIds` is JDK only, so the reactive gateway uses the
same rule as the servlet services.

| Member | Value |
| --- | --- |
| `HEADER` | `X-Request-Id`: the HTTP header, on requests and on responses |
| `MDC_KEY` | `requestId`: the SLF4J MDC key (`%X{requestId}` in a log pattern) |
| `AMQP_HEADER` | `x-request-id`: the RabbitMQ message header |
| `REQUEST_ATTRIBUTE` | `com.itways.requestId`: the servlet request attribute |
| `MAX_LENGTH` | `64` |
| `isWellFormed(id)` | non-null and `^[A-Za-z0-9._-]{1,64}$` |
| `accept(incoming)` | the trimmed value when it is well formed, otherwise `generate()` |
| `generate()` | a random UUID |

A missing, blank or too long value, or one with spaces, quotes or line breaks, is
replaced, so a caller cannot forge a log line through the header.

### Incoming requests (common-web)

- `@EnableCommon` imports `com.itways.common.config.CommonConfig`, which now also imports
  `com.itways.web.correlation.RequestCorrelationConfig` (configuration bean
  `requestCorrelationConfig`). `@EnableRequestCorrelation` (`com.itways.annotation`, in
  common-web) imports that configuration alone.
- `RequestCorrelationConfig` registers the bean `requestIdFilter`, a
  `FilterRegistrationBean<RequestIdFilter>`:

  | Setting | Value |
  | --- | --- |
  | Filter name | `requestIdFilter` (`RequestCorrelationConfig.FILTER_NAME`) |
  | URL pattern | `/*` |
  | Order | `Ordered.HIGHEST_PRECEDENCE` (`RequestCorrelationConfig.ORDER`). It runs ahead of the internal guard (`InternalEndpointGuardConfig.ORDER`, `HIGHEST_PRECEDENCE + 10`) and Spring Security (-100), so a refused request still has an id. |
  | Dispatcher types | `REQUEST`, `ASYNC`, `ERROR` |
  | Conditions | A servlet web application (`@ConditionalOnWebApplication(type = SERVLET)`), and `itways.request-id.enabled` not `false` |

- `RequestIdFilter` is a `OncePerRequestFilter`:
  - It takes `RequestIds.accept(...)` of the incoming `X-Request-Id`. An async or error
    re-dispatch reuses the id of the first pass, read from the request attribute.
  - It sets the response header `X-Request-Id` before the rest of the chain runs (unless
    the response is already committed), so a 401 from Spring Security or a streamed body
    has it too.
  - It stores the id as the request attribute `com.itways.requestId`.
  - It puts the id in the MDC under `requestId` for the duration of the request, then
    restores the previous value (or removes the key when there was none), so a pooled
    thread never carries it into the next request.
- `CurrentRequestId` reads the id back:
  - `get()` reads the MDC;
  - `of(request)` also falls back to the request attribute;
  - `stamp(body)` and `stamp(body, request)` set a body's `reference` when it has none.
- account, auth, channels, journey, template and speech get the filter through
  `@EnableCommon`. **notification-service has no `@EnableCommon`: add
  `@EnableRequestCorrelation` to `NotificationApplication`.**

### Error envelope

- `ApiResponse` (common-core) gains `reference`, annotated `@JsonInclude(NON_NULL)`. It is
  left out of the JSON when null, so success bodies, and error bodies written without an
  id, keep exactly their five fields. The five-argument constructor stays, so existing
  `new ApiResponse<>(…)` calls compile.
- Every error body the library writes carries the current id as `reference`:
  - `GlobalExceptionHandler`: the `error(...)` and `validationFailed(...)` hooks, the
    `BusinessException` answer and the 405 answer;
  - `DataAccessExceptionHandler` (409 `DATA_CONFLICT`);
  - `CustomErrorController` (`/error`), which falls back to the request attribute;
  - `ApiResponseAuthenticationEntryPoint` and `ApiResponseAccessDeniedHandler` (the shared
    401/403);
  - the 401 that `JwtAuthenticationFilter` writes itself;
  - the 404 of `InternalEndpointGuard`.
- The reference quoted inside a message is the request id when there is one, a UUID
  otherwise (`newReference()`). This covers `Internal server error (reference R)`, the
  hide-5xx answer and the 409 data conflict.
- An error body a service builds itself gets `reference` only when it goes through the
  base's hooks or calls `CurrentRequestId.stamp(...)`. This applies to:
  - account `config/AccountExceptionHandler` and auth `config/AuthExceptionHandler`:
    section 4 moves them onto the base, and their domain handlers get the field when they
    answer through `error(...)`;
  - template `templates/TemplateExceptionHandler` and speech
    `knowledge/KnowledgeFacadeErrors`: they build `ApiResponse.error(...)` directly.

  A subclass that overrides `error(...)` without calling `super` must stamp the body itself.

### Outbound HTTP (common-web)

`ForwardedCallerInterceptor` (on every `ServiceCalls` client) and the Feign interceptor of
`@EnableForwardedAuth` (`ForwardedAuthFeignConfig`) add `X-Request-Id` from
`CurrentRequestId.get()` next to the caller's credential. They skip it when the call
already names one. Both read the MDC, so a call made inside a message listener carries the
message's id too.

### Messaging (common-messaging)

The auto-configuration `com.itways.messaging.RabbitPublishingAutoConfiguration`
(`@AutoConfiguration(before = RabbitAutoConfiguration.class)`,
`@ConditionalOnClass(RabbitTemplate.class)`) registers three beans unless
`itways.request-id.messaging.enabled=false`:

| Bean | Type | What it does |
| --- | --- | --- |
| `requestIdPublishing` | `RabbitTemplateCustomizer`, `@ConditionalOnMissingBean(name = "requestIdPublishing")` | Adds a `RequestIdPublishPostProcessor` to Spring Boot's `RabbitTemplate` (`addBeforePublishPostProcessors`). A message published while the MDC holds a well-formed id gets the `x-request-id` header. A header the message already has is kept. Without an id, nothing is added. |
| `requestIdListenerAdvice` | `RequestIdListenerAdvice`, `@ConditionalOnMissingBean` | The advice, for a listener container a service builds by hand. |
| `requestIdListenerAdviceRegistrar` | `RequestIdListenerAdviceRegistrar`, a static `BeanPostProcessor` | Adds a `RequestIdListenerAdvice` to every `AbstractRabbitListenerContainerFactory` bean once it is initialised. |

**The listener advice goes in front of a factory's existing advice chain.** The registrar
keeps every advice already in the chain and puts the request-id advice first, as the
outermost one. A retry interceptor stays behind it, so every retry attempt, and the
recoverer's log lines after the last attempt, carry the id. A factory whose chain already
holds a `RequestIdListenerAdvice` is left alone. The factories concerned today:

- Spring Boot's `rabbitListenerContainerFactory` in every service. In notification it has
  Boot's listener retry (`spring.rabbitmq.listener.simple.retry.enabled=true`).
- speech's `chat/AccountEventsListenerConfig.chatAccountEventsListenerFactory`. Its
  `retryThenPark(...)` chain is set in the `@Bean` method, so it is already there when the
  registrar runs.

While a listener handles a message, the advice sets the MDC `requestId` from the message's
`x-request-id` header:

- The header may be a `String`, a `LongString` or a `byte[]`. It is trimmed and used only
  when it is well formed.
- A batch listener uses the first message's id.
- A message without the header, or with a malformed one, runs with the key removed, so an
  earlier message's id never leaks into it.
- The previous value is restored afterwards.

The listener's log lines, its `ServiceCalls` / Feign calls and the messages it publishes
therefore carry the id.

**Publishers:**

- `NotificationPublisher`, and the services' own publishers on Boot's `RabbitTemplate`, get
  the header from the `requestIdPublishing` post-processor. Their calls do not change.
- `ActivityEventPublisher` (`@EnableActivity` without the outbox) uses Boot's
  `RabbitTemplate` (`ActivityMqConfig`). It sets the header itself only when the caller
  filled the event's `requestId`. Otherwise it **relies on the `RabbitTemplate`
  post-processor** to add the header from the MDC. It never fills the event's `requestId`
  field itself.
- `ActivityOutbox.record(...)` and `recordIndependently(...)` fill an event's `requestId`
  from the MDC when the caller did not. The relay sends later, on its own thread, through
  `RabbitConfirmedSender`, which has its own `RabbitTemplate`. It sets the header from the
  event's `requestId` and does not rely on the post-processor.
- A `RabbitTemplate` built by hand must add `new RequestIdPublishPostProcessor()` with
  `addBeforePublishPostProcessors(...)`. A listener container built by hand must add the
  `requestIdListenerAdvice` bean to its chain. No service builds either today.

### Activity events

`AccountActivityEvent` (common-messaging) gains `requestId`, annotated
`@JsonInclude(NON_NULL)`. Events and stored outbox rows without one keep their former
JSON. The constructor with the twelve earlier fields stays. A consumer still on 1.0.13
ignores the new property: the library registers Spring AMQP's `Jackson2JsonMessageConverter`,
which does not fail on unknown properties.

### Logs

No service sets a log pattern today. To show the id, set this in each service's
`application.properties`:

```properties
logging.pattern.level=%5p [${spring.application.name:-},%X{requestId:-}]
```

Spring Boot's default console and file patterns print the level through this property. Every
line then shows the level followed by `[<service>,<id>]`, with nothing after the comma
outside a request or message.

### Gateway (GW-12, in api-gateway)

- A `GlobalFilter` takes the id with
  `RequestIds.accept(request.getHeaders().getFirst(RequestIds.HEADER))`.
- It forwards the id as `RequestIds.HEADER` and echoes it on the response.
- It logs the id under `RequestIds.MDC_KEY`.
- `ErrorResponseWriter` may add `reference` to its bodies.

### Tests to adjust

Six `@WebMvcTest`s assert that a 401/403 body has exactly five fields:
`jsonPath("$.*", hasSize(5))` in their `assertSecurityAnswer` helper. With the request-id
filter in the test context the body gains `reference`, and the assertion must become
`hasSize(6)`. Paths are relative to `workspace/`:

| Service | Test and line |
| --- | --- |
| account | `account-service/src/test/java/com/itways/assistant/account/config/AccountWebSecurityTest.java:438` |
| auth | `auth-service/src/test/java/com/itways/assistant/auth/signin/AuthWebTest.java:181` |
| channels | `channels-service/src/test/java/com/itways/assistant/channels/config/ChannelWebSecurityTest.java:179` |
| journey | `journey-service/src/test/java/com/itways/assistant/journey/config/SecurityRulesTest.java:269` |
| notification | `notification-service/src/test/java/com/itways/assistant/notification/config/NotificationWebSecurityTest.java:69` |
| template | `template-service/src/test/java/com/itways/assistant/template/config/TemplateWebSecurityTest.java:186` |

As written, each test's `@ContextConfiguration` class imports the service's
`SecurityConfig` and a few beans, but not `CommonConfig` or `RequestCorrelationConfig`.
The filter is therefore not in those contexts, and after a bare dependency swap the body
still has five fields. Production always has the filter (`@EnableCommon`, or
`@EnableRequestCorrelation` in notification). So that each test checks what production
sends:

- add `RequestCorrelationConfig` to the test's `@Import`;
- change the assertion to `hasSize(6)`;
- optionally, check that `$.reference` equals the `X-Request-Id` response header.

Any other test that asserts an exact error body, or the absence of `reference`, changes the
same way once the filter is in its context. Success bodies do not change.

## 6. Build order and verification

Use JDK 21: Lombok fails on newer JDKs.

```bash
mvn -f common-lib/pom.xml install         # the whole reactor: platform-bom, platform-parent, common-core, common-web, common-messaging
mvn -f file-storage-sdk/pom.xml install
mvn -f ai-engine-sdk/pom.xml install      # release 1.2.0 first: platform-bom pins 1.2.0
mvn -f journey-engine/pom.xml install     # journey-model + journey-engine-sdk 1.0.18
mvn -f journey-service/pom.xml install    # publishes journey-service-<version>-stubs.jar for the consumer contract tests
# then, in any order: account, auth, channels, template, speech, notification, api-gateway
mvn -f <service>/pom.xml verify
```

Workspace and build files:

- **Workspace aggregator.** `workspace/pom.xml` already lists `common-lib` first, and
  Maven orders the reactor by parent and dependency links.
- **`docker/java/Dockerfile`, `libs` stage (lines 23-25).** It builds common-lib from
  the 1.x layout:

  ```dockerfile
  COPY common-lib/pom.xml            common-lib/pom.xml
  COPY common-lib/src                common-lib/src
  RUN mvn $MVN_FLAGS -f common-lib/pom.xml install
  ```

  **`common-lib/src` no longer exists**, so the image build fails at that `COPY`. The
  stage must copy the reactor pom and the five module directories instead. Suggested
  lines:

  ```dockerfile
  COPY common-lib/pom.xml            common-lib/pom.xml
  COPY common-lib/platform-bom       common-lib/platform-bom
  COPY common-lib/platform-parent    common-lib/platform-parent
  COPY common-lib/common-core        common-lib/common-core
  COPY common-lib/common-web         common-lib/common-web
  COPY common-lib/common-messaging   common-lib/common-messaging
  RUN mvn $MVN_FLAGS -f common-lib/pom.xml install
  ```

  The workspace `.dockerignore` already leaves out `**/target`, so whole module
  directories are safe to copy.
- **In-house versions in `platform-bom`.** The BOM pins `ai-engine-sdk` 1.2.0,
  `file-storage-sdk` 2.0.1, and `journey-model` + `journey-engine-sdk` 1.0.18. Those
  releases are prepared in their own repositories. At the time of writing
  `file-storage-sdk/pom.xml` says 2.0.1 (`938899e`) and `journey-engine/pom.xml` says 1.0.18
  (`5a501ba`), but **`ai-engine-sdk/pom.xml` still says 1.1.0** (`d90d522`). The same
  `libs` stage installs whatever version each pom says. So a service that takes
  `ai-engine-sdk` from the BOM (speech, and journey-engine-sdk once it drops its pin) does
  not resolve until ai-engine-sdk 1.2.0 is built.
- **CI.** The CI workflows run `mvn -B -f common-lib/pom.xml install -DskipTests`, which
  still builds the reactor. Their comments about the `security-core` jar
  (`api-gateway/.github/workflows/docker.yml`) are stale.

Checklist for each consumer:

- [ ] `mvn dependency:tree` shows no `com.itways:common-lib` and no `security-core`
      classifier. It shows `common-web` / `common-messaging` (or `common-core` for the
      gateway) at 2.0.0 and the in-house SDKs at the `platform-bom` versions.
- [ ] Removed plugin and property pins are gone. `mvn help:effective-pom` shows JaCoCo
      0.8.13, surefire and failsafe 3.1.2, and Testcontainers 1.21.4.
- [ ] The application context starts: `mvn verify` includes the context-loading tests,
      and account's bean-name clash shows up here. auth starts only with
      `spring-boot-starter-freemarker` declared.
- [ ] `mvn verify` is green. Compare unit and integration-test counts with the run
      before the migration. No test deleted or disabled; tests of deleted classes move
      or are rewritten against the shared class.
- [ ] The deleted classes are gone: `grep -rn` for the class names in section 4 finds
      nothing.
- [ ] API surface diff (routes, parameters, return types, DTO property names) is
      identical before and after, apart from the documented error-body changes.
- [ ] `/actuator/prometheus` shows `account_activity_dlq_messages{queue="account.activity.dlq",…}`
      (account) and `notification_dlq_messages{queue="notification.dlq",…}`
      (notification).
- [ ] Responses carry `X-Request-Id`, and a request sent with a well-formed
      `X-Request-Id` gets the same value back. An error body has `reference` equal to
      that header. With the log pattern of section 5, log lines show `[<service>,<id>]`.
      A message published while serving the request carries `x-request-id`.
- [ ] Portal: after the services are redeployed, regenerate the API specs in
      `portal-web`: `npm run api:pull && npm run api:types`. `api:pull` reads the running
      services' `/v3/api-docs` and needs `API_DOCS_TOKEN`. Every `ApiResponse*` schema
      gains the optional `reference` property.

## 7. 2.1.0 (from 2.0.0)

An additive minor release for the brand-neutral naming (ARC-22, ARC-23). Nothing a
consumer compiles against is removed except a class that had no user left.

### What a consumer does

1. `<parent>` → `platform-parent` **2.1.0**. The BOM then gives `common-*` 2.1.0,
   `journey-model` / `journey-engine-sdk` **1.0.19**, `file-storage-sdk` **2.0.2** and
   `ai-engine-sdk` **1.2.1** (patch releases of 2.0.1 / 1.2.0 whose only change is the
   parent `platform-parent` 2.1.0; same code and dependencies).
2. Replace every literal `X-Nibras-Assistant` with `ScopeHeaders.ASSISTANT`: controller
   `@RequestHeader`s, Feign `@RequestHeader`s, local header constants, OpenAPI
   descriptions (a constant expression such as `"… the " + ScopeHeaders.ASSISTANT +
   " header"` is allowed in an annotation), and contract YAML. Outbound calls then send
   `X-Assistant-Id`.
3. Delete the secret default of the database password: remove the line
   `spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:12345}` (Spring Boot binds
   `SPRING_DATASOURCE_PASSWORD` to `spring.datasource.password` by itself). Do not write
   `${SPRING_DATASOURCE_PASSWORD}` without a default: Boot's binder leaves an unresolved
   placeholder in place as text and sends it as the password. Integration tests set
   their own password (`@DynamicPropertySource` or `@ServiceConnection`) and do not
   change.
4. Move explicit `RABBITMQ_*` placeholders to Spring Boot's own names: delete
   `spring.rabbitmq.*=${RABBITMQ_HOST:…}` (and `_PORT`, `_USERNAME`, `_PASSWORD`) or
   point them at `SPRING_RABBITMQ_*`. Compose already sets the `SPRING_RABBITMQ_*` names.
5. A service that hosts the journey engine (conversation-service) renames the engine's
   keys it sets, see below.
6. Portal: after the services are redeployed, `npm run api:pull && npm run api:types`;
   header parameters in the specs are now named `X-Assistant-Id`.

### Changed

- **`ScopeHeaders.ASSISTANT` is `X-Assistant-Id`** (was `X-Nibras-Assistant`). The old
  name is `ScopeHeaders.LEGACY_ASSISTANT`, deprecated for removal. Source-compatible;
  every `@RequestHeader(ScopeHeaders.ASSISTANT)` now binds and documents the new name.
- **The legacy name is still accepted**, so the portal and the services can move in
  any order:
  - `LegacyAssistantHeaderFilter` (bean `legacyAssistantHeaderFilter`, `/*`, order
    `HIGHEST_PRECEDENCE + 1`) comes with `@EnableCommon` and with
    `@EnableAssistantScope` (registered once when both are on). A request that sends
    only `X-Nibras-Assistant` reaches the service as if it had sent `X-Assistant-Id`;
    when both are sent, `X-Assistant-Id` wins and the request is untouched.
  - `@RequestedScope` reads `X-Assistant-Id`, else the legacy name, with or without
    the filter.
  - Each request that used the legacy name logs one DEBUG line
    (`com.itways.scope.AssistantHeader`).
  - common-lib itself sends no assistant header.
- Javadoc and comments name conversation-service (was speech-service /
  assistant-service) and no product brand.

### Removed

- **`com.itways.security.ApiKeyRevocationStore`** (bean `apiKeyRevocationStore`), the
  Redis deny-list of revoked API keys, deprecated for removal since the allow-list
  (`ApiKeyStatusStore`, AS-08) replaced it. No service reads or writes it any more
  (account-service stopped writing it; a comment there still names it). Its
  `nibras:apikeys:revoked:*` entries are inert and can be deleted.

### Added

- **`common.diagnostics.DatabaseLoginFailureAnalyzer`** (common-web,
  `META-INF/spring.factories`, always on): when a start fails because the database
  refused the login (SQL state `28P01`, `28000`, or PgJDBC's "no password was
  provided"), the startup report says "Set `SPRING_DATASOURCE_PASSWORD`" when no
  password is configured, else "Check `SPRING_DATASOURCE_USERNAME` and
  `SPRING_DATASOURCE_PASSWORD`". Other failures are left to Spring Boot's analyzers.

### journey-engine 1.0.19 (pinned by the BOM)

The engine's settings moved from the legacy product prefix to `journey.*`. The host
(conversation-service) must rename the keys it sets; unset keys keep their defaults.

| Old key | New key | Default |
| --- | --- | --- |
| `nibras.journey.api-call.allowed-hosts` | `journey.api-call.allowed-hosts` | empty (env `JOURNEY_API_ALLOWED_HOSTS` in conversation-service's properties) |
| `nibras.journey.api-call.block-private-networks` | `journey.api-call.block-private-networks` | `true` |
| `nibras.journey.api-call.connect-timeout-ms` / `read-timeout-ms` | `journey.api-call.connect-timeout-ms` / `read-timeout-ms` | `5000` / `30000` |
| `nibras.journey.script.statement-limit` / `timeout-seconds` | `journey.script.statement-limit` / `timeout-seconds` | `500000` / `10` |
| `nibras.journey.user-input.max-attempts` | `journey.user-input.max-attempts` | `3` |
| `nibras.journey.data-map.context-budget-chars` | `journey.data-map.context-budget-chars` | `8000` |
| `nibras.knowledge.synthesis.enabled` / `max-chunks` | `journey.knowledge.synthesis.enabled` / `max-chunks` | `true` / `3` |

A key still set under the old name is ignored after the upgrade: for
`allowed-hosts` that means private hosts are refused again until the key is renamed
(fail-safe, not fail-open).

### Unchanged on purpose (data identifiers)

Redis keys `nibras:auth:revoked-session:*`, `nibras:auth:pwchanged:*`,
`nibras:apikeys:active:*`, `nibras:apikeys:lastused:*`, `nibras:cache:*`; the engine's
run parameters `__nibras_conversation_id`, `__nibras_channel_capabilities`,
`__nibras_user_token` (stored in run history; journey-service's V8 migration and
redaction match the last one). No RabbitMQ name carries the prefix. See README,
"Names that keep the old product prefix".

### Build order

As in section 6, with `journey-engine` at 1.0.19:

```bash
mvn -f common-lib/pom.xml install
mvn -f file-storage-sdk/pom.xml install
mvn -f ai-engine-sdk/pom.xml install
mvn -f journey-engine/pom.xml install     # 1.0.19, parent platform-parent 2.1.0
mvn -f journey-service/pom.xml install    # the stubs for the consumer contract tests
```

`file-storage-sdk` 2.0.2 and `ai-engine-sdk` 1.2.1 name `platform-parent` 2.1.0 as
their parent, so a clean local repository (and the `libs` stage of
`docker/java/Dockerfile`) needs only this `common-lib` installed. Their previous
releases, 2.0.1 and 1.2.0, name `platform-parent` 2.0.0: Maven reads that parent both
to build them and to resolve them as a dependency, so a consumer still on
`platform-parent` 2.0.0 (whose BOM pins them) also needs `common-lib` 2.0.0 installed (`git worktree add <dir> v2.0.0`, then
`mvn -f <dir>/pom.xml install -DskipTests`).

Checklist, in addition to section 6's:

- [ ] `git grep -n X-Nibras-Assistant` in the service finds nothing (the literal lives
      only in `ScopeHeaders.LEGACY_ASSISTANT`).
- [ ] A request with only `X-Nibras-Assistant` still lists and creates for that
      assistant; one with `X-Assistant-Id` does too.
- [ ] `grep -n '12345\|RABBITMQ_HOST' src/main/resources/application.properties` finds
      nothing; the stack sets `SPRING_DATASOURCE_PASSWORD` for the service.

## 8. 2.2.0 (from 2.1.0)

An additive minor release for the knowledge-base upgrade (work package L1 of its plan).
Nothing a consumer compiles against is removed or changed: every 2.1.0 public
constructor, accessor and constant of `common-core` is still there (checked with
`javap` against the 2.1.0 classes), and a record that gained fields keeps its 2.1.0
constructor with the old meaning.

### What a consumer does

1. `<parent>` → `platform-parent` **2.2.0**. The BOM then gives `common-*` 2.2.0,
   `ai-engine-sdk` **1.3.0** and `journey-model` / `journey-engine-sdk` **1.0.20**
   (`file-storage-sdk` 2.0.2 is unchanged). Those three are released by their own
   work packages (L2, L3) after this one: see "Build order" below.
2. Nothing else is required. A service that serves or calls the knowledge base adopts
   the new contracts and helpers in its own work package.
3. A service that has routes only another platform service may call (journey-service's
   `/ingestion/**`) adds the service-token filter to its chain, see below.

### Added (common-core)

- `common.text.PiiScrubber`: `scrub(text)` replaces e-mail addresses with `[email]`
  and phone numbers with `[phone]` (ASCII, Arabic-Indic U+0660..0669 and Eastern
  Arabic-Indic U+06F0..06F9 digits; `+`/`00` international and local forms; 7..15
  digits; dates, years, prices and references glued to Latin letters are kept);
  `containsPii(text)`.
- `common.text.PassageHashes`: `sha256Hex(question, answer, locale)` is
  journey-service's V14 SQL formula byte for byte (`btrim` of spaces only, `"\n"`
  separators, null answer and locale as `""`; case and inner whitespace count);
  `sourceHash(hashes)` hashes the sorted passage hashes.
- `contracts.knowledge.KnowledgeIndexName`: the index-name rule (D6).
  `normalize` (strip + lower-case), `isValid` (`REGEX`
  `^[a-z0-9]+(?:[-_][a-z0-9]+)*$`, `MIN_LENGTH` 2, `MAX_LENGTH` 64), `isLegacy`,
  `isStorable` (not blank, no comma, ≤ 64: what an assistant may reference, legacy
  names included), `sameName` (case-insensitive), `slug(preview)` (`My FAQ ?` →
  `my-faq`; `""` when nothing valid can be built), `RULE` (the sentence for a 400).
  Create: `isValid(normalize(name))`, then a case-insensitive clash check.
- New records in `contracts.knowledge` (all `@JsonIgnoreProperties(ignoreUnknown = true)`):
  `KnowledgeSourceView` (with `KIND_*` / `STATUS_*` constants and `withUploadNotes`),
  `CreateSourceRequest` (+ `website(url)`), `SourcePassage` (+ `of(...)`, which computes
  the hash), `SourceDiff`, `ClaimRequest`, `SourcePatch` (+ `heartbeat`, `ready`,
  `failed`), `PendingPassage`, `CreateIndexRequest`, `RowsBulkRequest` (`ACTION_*`),
  `RowsBulkResult`, `KnowledgeSearchExplanation` (+ `withEmbeddingMs`), `KnowledgeHit`
  (`REASON_*`; `matchedTerms`, `termCoverage`, with a constructor without them), `KnowledgeHitDrop`
  (the same two, likewise), `GapApproveRequest` (+ `withVector`), `GapApproved`,
  `ParsedSheet`, `DroppedRow` (`NO_ANSWER`, `NO_QUESTION`), `IndexSettingsRequest`
  (`ignoreTerms`; `MAX_TERMS` 20, `MIN_TERM_LENGTH` 1, `MAX_TERM_LENGTH` 60: the body of
  journey's `PATCH /{index}/settings`, the knowledge-base embedding switch).

### Changed (common-core), additively

New trailing record components; the 2.1.0 constructor stays and fills them with the
old meaning. A 2.1.0 payload reads into the new record (the new fields absent), and a
2.2.0 payload reads into the 2.1.0 record (`ignoreUnknown`).

| Record | New components | The 2.1.0 constructor gives |
| --- | --- | --- |
| `KnowledgeSearchRequest` | `query`, `threshold`, `diversity`, `Boolean recall` | `null` ×4: vector-only, no gate (the 9-argument constructor: `recall = null`, a serving search) |
| `KnowledgeIndexSummary` | `sources`, `passages`, `pending`, `status`, `updatedAt`, `legacyName`, `ignoreTerms` | `passages = rowCount`, `READY`, `legacyName = isLegacy(name)`, `ignoreTerms = List.of()` (never null; the 12-argument constructor gives it too) |
| `KnowledgeRow` | `sourceId`, `sourceName`, `sourceKind`, `url`, `locale`, `Boolean enabled`, `contentHash` | `enabled = TRUE`, the rest `null` |
| `PatchUpsert` | `sourceId`, `Boolean enabled`, `contentHash` | `null` ×3 (journey keeps the row's flag, computes the hash) |
| `GapReport` | `indexNames`, `source` (`SOURCE_KNOWLEDGE_STEP`, `SOURCE_FALLBACK`) | `null` ×2 |
| `GapRecorded` | `merged` | `false` |
| `GapGroup` | `hitCount`, `indexNames`, `source` | `hitCount = count`, `List.of()`, `null` |

`KnowledgeRow.enabled` is a `Boolean`, not a `boolean`: a 2.1.0 payload has no such
field, and a primitive would read it as `false` (every row disabled on a later save).
`KnowledgeSourceView` gained `ignoreTerms` after its first 2.2.0 shape: null except in
journey's answer to `POST /ingestion/claim` (`withIgnoreTerms`), and the 18-argument
constructor (with the upload notes) stays. `KnowledgeSource` is deprecated for removal: journey answers `KnowledgeSourceView`,
which also writes `sourceFile`, `chunks` and `lastIngested` (read-only JSON aliases) for
one release, so a 2.1.0 reader of the source list keeps working.

### Added (common-web): service calls (D4)

- `security.internal.ServiceTokenAuthenticationFilter`: a request that carries a valid
  `X-Service-Token` (`InternalServiceToken.matches`: trimmed, constant-time) and **no**
  tenant credential (no `Authorization`, no `X-API-KEY`) gets a service-call session:
  principal `platform-service`, authority `SERVICE`, details
  `{authSource: SERVICE_TOKEN}`, no account. A request with a user's credential stays
  the user's (an invalid one stays unauthenticated, so still 401), an existing session
  is never replaced, a wrong token is logged without its value, and the filter never
  answers itself. `serviceAuthentication()` builds that session for tests.
- `security.internal.ServiceTokenAuthenticationConfig` (imported by
  `@EnableCustomSecurity`): bean `serviceTokenAuthenticationFilter`, only when
  `itways.internal-token` is set and not blank, with a **disabled**
  `FilterRegistrationBean` (`serviceTokenAuthenticationFilterRegistration`) so the
  container never runs it outside a chain.
- `Sessions`: `isServiceCall(auth)`, `serviceCall()` (an `AuthorizationManager`),
  `AUTH_SOURCE_SERVICE_TOKEN`, `AUTHORITY_SERVICE`, `SERVICE_PRINCIPAL`. `kindOf` says
  `NONE` for a service call (no new `CredentialKind` constant, so an exhaustive
  `switch` such as account-service's `ActivityRecorder` still compiles), hence
  `userSession()` refuses it.

A service that adopts it:

```java
private final ObjectProvider<ServiceTokenAuthenticationFilter> serviceTokenFilter;
...
.requestMatchers(KNOWLEDGE + "/ingestion/**").access(Sessions.serviceCall())   // before the broader rules
...
.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
serviceTokenFilter.ifAvailable(f -> http.addFilterBefore(f, UsernamePasswordAuthenticationFilter.class));
```

`authenticated()` admits a service call too: a route that reads `@AccountId` gets
`null` for one. Put the service-only rules first, and use `userSession()` (or an explicit
rule) where an account is needed.

### Build order

```bash
mvn -f common-lib/pom.xml install     # 2.2.0 (platform-bom, platform-parent first on an empty ~/.m2: make bootstrap)
mvn -f ai-engine-sdk/pom.xml install  # 1.3.0 (L2), parent platform-parent 2.2.0
mvn -f journey-engine/pom.xml install # 1.0.20 (L3): journey-engine-sdk depends on ai-engine-sdk, so after 1.3.0
mvn -f journey-service/pom.xml install
```

Until `ai-engine-sdk` 1.3.0 and `journey-*` 1.0.20 are installed, a build on
`platform-parent` 2.2.0 that depends on them cannot resolve them; `ai-engine-sdk`
itself and services that use neither build at once.

Checklist:

- [ ] The service's own tests pass on 2.2.0 unchanged (only additions).
- [ ] A service with service-only routes: a call with only the token reaches them; the
      same call with a user's token, an API key or a wrong token does not.

## 9. 2.3.0 (from 2.2.0)

> The product concept these names belong to was renamed in 2.5.0 (section 11): the
> 2.3.0 names below are kept as they shipped; section 11 maps them to the current ones.

An additive minor release for the integrations feature (work package WP-1 of its
design, `Documents/Architecture/connectors-design-2026-10-03.html`). Nothing a
consumer compiles against is removed or changed: `MailSecrets` and `ChannelSecrets`
keep every public constructor and method, and every value they sealed before opens
unchanged (`SealedSecretsGoldenVectorsTest` opens values produced by the 2.2.0
classes from a committed fixture).

### What a consumer does

1. `<parent>` → `platform-parent` **2.3.0**. The BOM then gives `common-*` 2.3.0; the
   SDK pins are unchanged (`ai-engine-sdk` 1.3.0, `file-storage-sdk` 2.0.2,
   `journey-model` / `journey-engine-sdk` 1.0.20) until journey-engine 1.1.0 (WP-2) is
   released and pinned here.
2. Nothing else is required. No stored value changes: `ms:` and `cs:` values keep their
   bytes, their key ids and their associated data, so no reseal or migration runs.
3. journey-service, when it adds integrations (WP-3), puts `@EnableIntegrationSecrets`
   on its application class and gets `INTEGRATION_SECRETS_KEY` in compose and `.env`.
4. account-service's activity consumer sees the new `ActivityCategory.INTEGRATION`
   once it is on 2.3.0; it stores the category as text, so events of that category
   arriving earlier are not rejected by the enum, but a consumer still on 2.2.0 that
   deserialises `AccountActivityEvent` with Jackson would fail on the unknown enum value.
   Release account-service on 2.3.0 before journey-service publishes `INTEGRATION`
   events.

### Added (common-web): `encryption.SealedSecrets`

The one single-key AES-256-GCM cipher behind the three secret kinds, parameterised:

- `new SealedSecrets(prefix, currentKey, previousKey)` (prefix such as `"is:"`, 32-byte
  keys; `decodeKey(base64, variable)` reads one from a setting with the usual messages);
  `seal(plain, aad)`, `open(sealed, aad)`, `reseal(value, aad)`, `isCurrent(value)`,
  `isSealedValue(value)`, static `isSealed(value, prefix)`, `prefix()`,
  `currentKeyId()`, static `context(parts...)` (`a|b|c` as UTF-8).
- Wire format unchanged: `<prefix><kid>:` + Base64(12-byte nonce + ciphertext + tag),
  `kid` = first 8 hex of SHA-256(key). The associated data is per call; the prefix is a
  label, not part of the binding, so each kind of secret has its own context.
- `MailSecrets` (`ms:`, context `mail-secret`) and `ChannelSecrets` (`cs:`, context
  `channel-secret`) are now thin subclasses: same constructors, same `seal`/`open`/
  `reseal`/`isCurrent`/static `isSealed` (mail) and `encrypt`/`decrypt`/`isCurrent`
  (channel), same pass-through of legacy plain values, same startup messages. Failure
  messages say "sealed"/"open" for both kinds now (channel used to say
  "encrypted"/"decrypt"); no consumer asserted on them. They also expose the base
  class's `prefix()`, `currentKeyId()` and the two-argument methods.
- `IntegrationSecrets` (`is:`): `seal(plain, instanceId, accountId, field)`,
  `open(sealed, instanceId, accountId, field)`, `reseal(...)`, static `isSealed(value)`,
  static `context(instanceId, accountId, field)` = `instanceId|accountId|field`. No legacy
  plain values: `open` refuses anything without the `is:` prefix.
- `IntegrationSecretsConfig` and `@EnableIntegrationSecrets` (`com.itways.annotation`),
  the `MailSecretsConfig` shape: bean `integrationSecrets` from
  `integration.secrets.key` (default env `INTEGRATION_SECRETS_KEY`) and
  `integration.secrets.previous-key` (`INTEGRATION_SECRETS_KEY_PREVIOUS`); required
  unless `itways.integration-secrets.required=false` (then no bean and a WARN when
  blank); a service's own `IntegrationSecrets` bean wins.

### Added (common-core): `common.net.HostAllowList`

The allow-list rule that journey-engine's `EgressGuard`, notification-service's
`TenantMailGuard` and common-web's `PublicOnlyDnsResolver` each copied, hoisted once:

- `HostAllowList.parse("a.example, .corp.internal, 10.0.0.5, [fd00::5]")` or
  `of(Collection<String>)`; `EMPTY`; `allows(String host)`, `allows(URI)` (the host
  only; the `user@host` trick and ports do not change the verdict), `isEmpty()`,
  `entries()`, static `normalize(host)`.
- Entries: an exact host name, an IP literal, or a `.domain` suffix (the domain and its
  subdomains). Hosts and entries are compared normalised (lower case, no trailing dot,
  no IPv6 brackets, IDN as punycode, one IPv6 spelling). Numeric shorthand (`127.1`,
  `2130706433`, `0x7f000001`, leading-zero octets) never matches an address entry; a
  suffix never matches an IP literal.
- An entry that is not a host (a URL, `host:port`, `user@host`, `*.domain`, `.`) is
  refused with an `IllegalArgumentException` when the list is built. This is the one
  behaviour change a consumer can see: `PublicOnlyDnsResolver(Collection<String>)` now
  fails at construction on such an entry instead of silently never matching it.
  `EgressGuard` and `TenantMailGuard` are untouched in this release.

### Added (common-messaging)

`ActivityCategory.INTEGRATION`, for the `INTEGRATION_*` and `CONNECTOR_TYPE_*`
activity entries journey-service will write.

### Build order

```bash
mvn -f common-lib/pom.xml install     # 2.3.0 (platform-bom, platform-parent first on an empty ~/.m2: make bootstrap)
mvn -f journey-engine/pom.xml install # 1.1.0 (WP-2), parent platform-parent 2.3.0
mvn -f journey-service/pom.xml install
```

Checklist:

- [ ] The service's own tests pass on 2.3.0 unchanged (only additions).
- [ ] Stored `ms:` / `cs:` values still open (the golden-vector test proves the format;
      a service's own round-trip tests confirm its wiring).
- [ ] A service that builds a `PublicOnlyDnsResolver` from an operator list: every
      entry is a host name, an IP literal or a `.domain` suffix.

## 10. 2.4.0 (from 2.3.0)

Only additions: the virtual Shared workspace in `com.itways.scope` (README,
"The Shared workspace (2.4.0)"). `SelectedScope`, `ScopeHeaders.SHARED_VALUE`,
`AssistantHeader` public (`read` → `SelectedScope`, `raw` → `String`),
`ScopeRules.forCreate` / `ownerForCreate` overloads taking a `SelectedScope`,
`ScopeErrors.ownerRequired`.

Checklist for a service:

- [ ] Parent `platform-parent` 2.4.0.
- [ ] Controllers that bind `X-Assistant-Id` as `@RequestHeader UUID` bind a
      `String` and call `SelectedScope.parse` (or `AssistantHeader.read`), then pass
      the `SelectedScope` to `ScopeRules`; otherwise the header value `shared` is a
      400 before the service sees it.
- [ ] Tests that mock `forCreate` / `ownerForCreate` with an untyped `any()` for the
      selected assistant type it (`any(UUID.class)` or `any(SelectedScope.class)`).

## 11. 2.5.0 (from 2.4.0)

The product concept is renamed to "connectors" everywhere in the library. Nothing
else changes; the release breaks the compile of the one consumer that uses the old
names (journey-service) and the activity data of account-service.

| 2.3.0 / 2.4.0 | 2.5.0 |
| --- | --- |
| `com.itways.encryption.IntegrationSecrets` | `com.itways.encryption.ConnectorSecrets` |
| `com.itways.encryption.IntegrationSecretsConfig` (bean `integrationSecretsConfig`) | `com.itways.encryption.ConnectorSecretsConfig` (bean `connectorSecretsConfig`) |
| `@com.itways.annotation.EnableIntegrationSecrets` | `@com.itways.annotation.EnableConnectorSecrets` |
| bean `integrationSecrets` | bean `connectorSecrets` |
| property `integration.secrets.key` / `integration.secrets.previous-key` | `connector.secrets.key` / `connector.secrets.previous-key` |
| env `INTEGRATION_SECRETS_KEY` / `INTEGRATION_SECRETS_KEY_PREVIOUS` | `CONNECTOR_SECRETS_KEY` / `CONNECTOR_SECRETS_KEY_PREVIOUS` |
| property `itways.integration-secrets.required` | `itways.connector-secrets.required` |
| failure messages "integration secret" | "connector secret" |
| `ActivityCategory.INTEGRATION` | `ActivityCategory.CONNECTOR` |

Unchanged on purpose: the `is:` prefix of sealed values, the key id and the
associated data (`instanceId|accountId|field`), so every value sealed under 2.3.0 or
2.4.0 opens with `ConnectorSecrets` under the same key, without a reseal.

Checklist for a service:

- [ ] Parent `platform-parent` 2.5.0.
- [ ] journey-service: the class, annotation and bean names above; the key moves to
      `CONNECTOR_SECRETS_KEY` (same value) in compose and `.env`.
- [ ] account-service: a data migration rewrites stored activity category
      `INTEGRATION` to `CONNECTOR` before the new enum reads them; release it with or
      before the journey-service that publishes `CONNECTOR`.

## Commits read

| Repository | Commit |
| --- | --- |
| common-lib | `d0bc6a4` (ARC-25), on top of `f645220` (ARC-11), `d00e0e9` (ARC-10) and `9d8d7ff` (ARC-04) |
| account-service | `1e8adfb` |
| auth-service | `5dee8e6` |
| channels-service | `bc79200` |
| journey-service | `4555490` |
| template-service | `ed0ea47` |
| speech-service | `7e415ed` |
| notification-service | `113235a` |
| api-gateway | `eeff93b` |
| ai-engine-sdk | `d90d522` |
| file-storage-sdk | `938899e` |
| journey-engine | `5a501ba` |
