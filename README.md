# common-lib

The shared Java library of the platform's Spring Boot services: credential checks,
the response envelope and error handling, tenant scoping, caching, messaging
contracts and secret sealing. Every backend service depends on it; the
api-gateway depends only on its framework-free `security-core` jar.

- Group / artifact: `com.itways:common-lib`
- Java 21, Spring Boot 3.2.x (the version is set by `spring-boot.version` in `pom.xml`)

## Build

The library is not published to a remote repository. Install it into the local
Maven repository **before** building any service:

```bash
mvn -f common-lib/pom.xml install          # runs the tests; -DskipTests to skip them
```

`docker/java/Dockerfile` does the same in its `libs` stage, and each service's
CI installs it first. On a host whose JDK is newer than 21, Lombok fails with
"cannot find symbol"; build in the `maven:3.9-eclipse-temurin-21` image.

`install` produces three jars:

| Jar | Contents | Used by |
| --- | --- | --- |
| `common-lib-<v>.jar` | everything below | account, auth, channels, journey, notification, speech, template |
| `common-lib-<v>-security-core.jar` | only `com.itways.security.core` and `contracts.channels.ChannelWebhookTokenClaims` | api-gateway (WebFlux) |
| `common-lib-<v>-sources.jar` | sources | IDEs |

## Version policy

- All services pin the same version (`1.0.13` today). A change that alters behaviour
  every service sees (a startup check, a new default) is announced in the open-points
  tracker and verified against every consumer's test suite before it is copied in.
- Bump the version when a change breaks a consumer's compile or needs a consumer-side
  change; update every `pom.xml` that pins it (the gateway's `common-lib.version`
  property included) in the same change.
- Keep `com.itways.security.core` framework-free (`SecurityCoreIsFrameworkFreeTest`):
  the gateway loads it without Spring MVC. The gateway's `CommonLibCompatibilityTest`
  checks that both jars judge credentials alike.

## How a service opts in

Nothing is active just by being on the classpath except what is listed under
"Always on". The rest is switched on with an annotation on the application class:

| Annotation | Imports | What the service gets |
| --- | --- | --- |
| `@EnableCommon` | `common.config.CommonConfig` | `ApiResponse` / `PageResponse`, `GlobalExceptionHandler`, `DataAccessExceptionHandler`, `CustomErrorController`, UTC time, RestTemplate, OpenAPI schema helpers |
| `@EnableCustomSecurity` | `security.config.SecurityConfig` (scans `com.itways.security`) + `@EnableCache` | `JwtTokenProvider`, `JwtAuthenticationFilter`, `ApiKeyAuthenticationFilter`, `SecurityUtils`, revocation / API-key allow-list stores, `@AccountId` resolver, `InternalServiceToken`, the shared 401/403 handlers |
| `@EnableCache` | `cache.config.CacheConfig` + `@EnableCaching` | `CacheStoreFactory` (Ehcache, Redis, hybrid) and the bounded `CacheManager` |
| `@EnableAssistantScope` | `scope.AssistantScopeConfig` | `AssistantDirectory`, `ScopeRules`, `@RequestedScope ListScope` parameters (needs a `JdbcTemplate`) |
| `@EnableActivity` | `activity.config.ActivityConfig` | `ActivityEventPublisher` (account activity over RabbitMQ); with `itways.activity.outbox.enabled=true` also `ActivityOutbox` and its relay (see "Activity outbox") |
| `@EnableNotifications` | `notification.config.NotificationConfig` | `NotificationPublisher`, `notification.queue` (declared argument-free: every sender declares it) |
| `@EnableEncryption` | `encryption.EncryptionConfig` (scans `com.itways.encryption`) | `EncryptionService`, `RsaService` and the sealing helpers of that package |
| `@EnableForwardedAuth` | `feign.ForwardedAuthFeignConfig` | Feign interceptor that forwards the caller's credential and `X-Service-Token` (needs Feign) |
| `@EnableFreeMarker` | `freemarker.FreeMarkerConfig` | `TemplateRender` |
| `@EnableAccountAuditing` | `jpa.AccountAuditingConfig` | JPA auditing of the account id (needs Spring Data JPA) |

A service's own `SecurityFilterChain` decides who may call what; common-lib's filters
only establish who the caller is.

### Always on (auto-configuration)

- `cache.config.CacheAutoConfiguration`: the cache beans, ordered before Spring
  Boot's cache auto-configuration.
- `common.config.SwaggerConfig`.
- `security.config.GeneratedUserFilter` (`META-INF/spring.factories`): leaves out
  Spring Boot's `UserDetailsServiceAutoConfiguration`, see below.

## Packages

| Package | What it holds |
| --- | --- |
| `security.core` | Framework-free credential rules: `TokenVerifier` (JWT, platform and channel-webhook keys, rotation), `ApiKeyCodec` (`X-API-KEY` format), `CredentialCrypto` (AES-256-GCM, SHA-256), `PublicKeys`. Packaged alone as the `security-core` jar. |
| `security`, `security.jwt`, `security.servlet` | The Spring side: `JwtTokenProvider`, `SecurityUtils`, `ApiKeyProvider`, `SessionRevocationStore`, `ApiKeyStatusStore`, the two servlet filters, `ApiResponseAuthenticationEntryPoint` / `ApiResponseAccessDeniedHandler`. |
| `security.internal` | `InternalServiceToken`: recognises another platform service on `/internal/` routes (`X-Service-Token`, `itways.internal-token`). |
| `security.config`, `security.resolver`, `security.annotation` | Security wiring, `@AccountId`. |
| `scope` | Per-assistant scoping: `AssistantScope`, `ScopeRules`, `ListScope`, `RequestedScopeArgumentResolver`, `ScopeHeaders`, `ScopeErrors`. |
| `cache` | `CacheStore` / `CacheStoreFactory` with Ehcache, Redis and hybrid stores; `CacheProperties`. |
| `amqp`, `notification`, `activity` | RabbitMQ JSON conversion; notification and activity publishers and their DTOs. |
| `activity.outbox` | The transactional outbox for activity events: `ActivityOutbox`, `ActivityOutboxRelay`, `ActivityOutboxStore`, `RabbitConfirmedSender`, the health indicator and gauges. |
| `contracts` | Payloads services exchange: `account`, `channels`, `journey`, `knowledge`, `template` (`TemplateVariable` keeps its `optional` flag and 3-argument constructor). |
| `encryption` | `ChannelSecrets` (`CHANNEL_SECRETS_KEY`: channel provider secrets), `MailSecrets` (`MAIL_SECRETS_KEY`: SEND_MAIL SMTP passwords), `EncryptionService`, `RsaService`. |
| `common.net` | `PublicUrlPolicy`: whether a tenant-supplied URL may be called (SSRF guard). |
| `common` | Envelope, error codes, exception handlers, time and ref utilities. |
| `feign`, `freemarker`, `jpa` | The opt-in integrations above. |

## Security defaults

### Keys (fail at startup)

`JwtTokenProvider` exists only in services with `@EnableCustomSecurity`, and there it
needs the platform's public key. Without it the service does not start:

| Variable (property) | Required | On a bad value |
| --- | --- | --- |
| `RSA_PUBLIC_KEY` (`jwt.rsa.public-key`) | yes | startup fails and names the variable |
| `RSA_PRIVATE_KEY` (`jwt.rsa.private-key`) | no: only a service that mints tokens (auth-service) needs it | startup fails and names the variable |
| `CHANNEL_WEBHOOK_PUBLIC_KEY` (`jwt.channel-webhook.public-key`) | no: without it webhook tokens are verified with the platform key | startup fails and names the variable |
| `CHANNEL_WEBHOOK_PUBLIC_KEY_PREVIOUS` (`jwt.channel-webhook.previous-public-key`) | only during a webhook key rotation, and only with the current key | startup fails and names the variable |

Keys are Base64 X.509 (public) or PKCS#8 (private); PEM armour is accepted for public
keys. Before this rule a service without a key generated a throwaway pair, started
and refused every real token (CH-15). The compose file passes the public keys to every
service through `x-common-env`. notification-service has no `JwtTokenProvider` and needs
none of them.

### 401 and 403 answers

`ApiResponseAuthenticationEntryPoint` answers 401 `AUTH_401` ("Authentication is
required") and `ApiResponseAccessDeniedHandler` answers 403 `AUTH_403`, both in the
`ApiResponse` envelope, the same body the api-gateway sends at the edge. With
`@EnableCustomSecurity` both are beans (each backs off if the service declares its
own); a chain uses them only when it says so:

```java
http.exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint)
        .accessDeniedHandler(accessDeniedHandler));
```

Chains that do not reference them (notification-service, template-service, and the
services with their own messages) behave as before.

### Revoked access tokens (PLT-05)

`JwtAuthenticationFilter` refuses a user ACCESS token that auth-service has revoked,
with the shared 401 `AUTH_401` ("Your session has ended. Please sign in again.")
written by `ApiResponseAuthenticationEntryPoint`, so every service answers 401 even
where its chain names no entry point. `SessionRevocationStore` holds two kinds of
Redis entry, written only by auth-service:

| Key | Value | TTL | Written on |
| --- | --- | --- | --- |
| `nibras:auth:revoked-session:<sid>` | `1` | access-token lifetime + 1 min (16 min) | sign-out; a refresh token reused after rotation |
| `nibras:auth:pwchanged:<accountId>` | cut-off, epoch seconds | `security.session-revocation.ttl` (2 days) | password change or reset; deactivation (`users.sessions_revoked_at`) |

- An access token names its session in the `sid` claim (the refresh token's
  session). It is revoked when its session entry exists or its `iat` is strictly
  before the account's cut-off. The second key keeps its historic name so a
  service on the previous common-lib keeps reading it during a rolling deploy.
- One Redis round trip per bearer request: `GET` of the cut-off, or `MGET` of both
  keys when the token has a `sid`. Nothing is cached.
- Fail open: Redis unreachable or no `StringRedisTemplate` bean means "not
  revoked", with at most one WARN a minute. The alternative would sign everyone out
  whenever Redis blips; the exposure is bounded by the 15-minute token lifetime.
  (The API-key allow-list, `ApiKeyStatusStore`, fails closed instead: losing an
  allow-list entry cannot resurrect a revoked key.)
- Access tokens minted before `sid` existed are judged by the cut-off only and end
  at their expiry. Channel webhook and refresh tokens are not checked here.
- The api-gateway checks signatures only; revocation is enforced in the services.

### No generated password

Spring Boot creates an in-memory user and logs "Using generated security password"
whenever a service has no `UserDetailsService`, which is every platform service.
`GeneratedUserFilter` leaves that auto-configuration out wherever common-lib is on the
classpath, test slices included. A service's own `exclude` of it still works. To get
Boot's user back: `itways.security.default-user.enabled=true`.

## Activity outbox (PLT-07)

`ActivityEventPublisher` sends an event to RabbitMQ on the side and logs a failure:
a broker outage, or a crash between the commit and the send, loses the event. A
service that sets `itways.activity.outbox.enabled=true` records events in its own
outbox table instead. Services that leave it off keep the publisher, unchanged.
auth, journey, channels and template have it on.

**Writing.** `ActivityOutbox.record(event)` inserts a row in the current
transaction, so the event commits and rolls back with the change it describes;
without a transaction (or in a read-only one) it writes in its own. A failed insert
fails the caller: the change and its entry go together.
`recordIndependently(event)` writes in its own transaction whatever the caller does
next, for an attempt the caller is about to refuse (auth's failed sign-ins and
codes, rejected password changes, reused refresh tokens), and only logs a failed
insert. The event keeps a stable `eventId` (set when missing); the wire format is
unchanged.

**The table** is created by the service's own Flyway migration, named with the
service's prefix because the services share one database:

```sql
CREATE TABLE IF NOT EXISTS <service>_activity_outbox (
    id              uuid         NOT NULL,   -- the eventId
    payload         jsonb        NOT NULL,   -- the AccountActivityEvent
    created_at      timestamptz  NOT NULL DEFAULT now(),
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    sent_at         timestamptz,
    last_error      varchar(500),
    CONSTRAINT pk_<service>_activity_outbox PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_<service>_activity_outbox_due
    ON <service>_activity_outbox (next_attempt_at) WHERE sent_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_<service>_activity_outbox_sent
    ON <service>_activity_outbox (sent_at) WHERE sent_at IS NOT NULL;
```

| Service | Table | Migration |
| --- | --- | --- |
| auth-service | `auth_activity_outbox` | V3 |
| journey-service | `journey_activity_outbox` | V13 |
| channels-service | `channels_activity_outbox` | V5 |
| template-service | `template_activity_outbox` | V8 |

**Relaying.** `ActivityOutboxRelay` runs on its own thread (not `@Scheduled`, which
would switch on the services' other scheduled methods): every `poll-interval`, and
right after each commit that wrote an event. A round claims up to `batch-size` due
rows with `FOR UPDATE SKIP LOCKED`, publishes them, waits for the broker's
confirms and sets `sent_at`, in one transaction; another instance skips the locked
rows, so two relays never send one row. The services' connection factory has no
publisher confirms, so the relay opens its own connection (`activity-outbox`) built
by Spring Boot's configurers with `SIMPLE` confirms. An unconfirmed batch gets
`attempts + 1` and `next_attempt_at = now + initial-backoff * 2^attempts` (at most
`max-backoff`) per row, with the error in `last_error`. Sent rows are deleted after
`retention`. Delivery is at least once (a crash after the confirm sends again, same
`eventId`); account-service stores an id once.

**Watching.** Gauges `activity.outbox.pending` and
`activity.outbox.oldest.pending.age` (seconds), tagged `table`, -1 while unknown,
read every `stats-interval`. Health component `activityOutbox`: `UP`, or `WARNING`
when the oldest unsent row is older than `lag-warning` (with one WARN log line).
`WARNING` is outside Spring Boot's status order, so it never changes the aggregate
status or readiness.

## Configuration properties

| Property | Default | Meaning |
| --- | --- | --- |
| `itways.cache.manager.maximum-size` | `10000` | Entries per cache of the default `CacheManager` (`@Cacheable`); least recently used go first. |
| `itways.cache.manager.ttl` | `10m` | Lifetime of an entry after it was written. |
| `itways.cache.provider` | `ehcache` | Backing store of `CacheStoreFactory`: `redis` uses Redis while it is reachable and Ehcache otherwise; anything else uses Ehcache. |
| `itways.cache.ehcache.heap-size` / `ttl-minutes` | `1000` / `10` | Ehcache store defaults. |
| `itways.cache.redis.ttl-minutes` | `10` | Redis store default TTL. |
| `itways.security.default-user.enabled` | `false` | `true` keeps Boot's generated in-memory user. |
| `security.session-revocation.ttl` | `2d` | How long an account cut-off (`nibras:auth:pwchanged:*`) is kept; also the fallback TTL of a revoked session. Must exceed the access-token lifetime. |
| `jwt.encryption.key` (`JWT_ENCRYPTION_KEY`) | none, required | AES key for tenant binding and API keys (`SecurityUtils`). |
| `itways.internal-token`, `itways.internal-token-enforce` | none, `false` | `X-Service-Token` for internal routes. |
| `itways.activity.outbox.enabled` | `false` | Records activity events in the outbox table (needs a `DataSource`, a transaction manager and Boot's RabbitMQ auto-configuration). |
| `itways.activity.outbox.table` | none, required when enabled | The service's outbox table; lower-case identifier. |
| `itways.activity.outbox.relay-enabled` | `true` | Whether this instance runs the relay (writes happen either way). |
| `itways.activity.outbox.poll-interval` / `batch-size` | `2s` / `100` | How often the relay looks for due rows, and how many one round sends. |
| `itways.activity.outbox.confirm-timeout` | `10s` | How long RabbitMQ has to confirm a batch. |
| `itways.activity.outbox.initial-backoff` / `max-backoff` | `1s` / `5m` | Retry wait of an unconfirmed row, doubled per attempt. |
| `itways.activity.outbox.retention` / `cleanup-interval` | `7d` / `1h` | How long sent rows are kept, and how often they are deleted. |
| `itways.activity.outbox.stats-interval` / `lag-warning` | `15s` / `5m` | How often the gauges are read; the age that turns the health component to `WARNING`. |

The default `CacheManager` is a Caffeine manager: caches are created on first use under
any name, each bounded by the two properties above. It replaced an unbounded
`ConcurrentMapCacheManager`. A service that declares its own `CacheManager` bean keeps
it. The Redis and Ehcache `CacheStore`s are separate and unchanged.

## Tests

`mvn test` runs the library's unit tests, among them:

- `SecurityCoreIsFrameworkFreeTest`: `security.core` imports nothing but the JDK and jjwt.
- `TokenVerifierTest`, `ApiKeyCodecTest`, `CredentialCryptoTest`, `SecurityCoreDelegationTest`: the credential rules and the Spring classes that delegate to them.
- `JwtAuthenticationFilterTest`: who gets a session, who gets 401, who continues unauthenticated.
- `AccessTokenRevocationTest`: the filter with the real `SessionRevocationStore` on a mocked Redis (revoked session, cut-off, no `sid`, Redis down, no Redis).
- `JwtTokenProviderStartupTest`: missing or broken keys fail the startup.
- `SecurityErrorAnswersTest`, `GeneratedUserFilterTest`, `DefaultCacheManagerTest`.
- `ScopeRulesTest`, `ListScopeTest`, `ChannelSecretsTest`, `MailSecretsTest`, `PublicUrlPolicyTest`, `NotificationPublisherTest`.
- `ActivityOutboxTest`, `ActivityOutboxRelayTest`, `ActivityOutboxConfigTest`: the outbox's transaction rules, retries and backoff, health and gauges, and that it stays off without the property.

`mvn verify` also runs `ActivityOutboxIT` (Testcontainers: Postgres and RabbitMQ): a
rollback writes and sends nothing, a commit sends exactly once, a broker outage keeps
the rows and sends them after recovery, two relays never send one row twice, sent rows
go after the retention.

A change here is verified against every consumer's own suite before it ships.
