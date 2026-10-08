# common-lib

The shared Java code of the platform's Spring Boot services, built as one Maven
reactor of five modules: the parent POM and BOM every service builds with, and the
shared library split in three jars by what they need at runtime.

Upgrading from 1.0.13: see [MIGRATION-2.0.md](MIGRATION-2.0.md); from 2.0.0 to 2.1.0:
its section 7; from 2.1.0 to 2.2.0: its section 8; from 2.2.0 to 2.3.0: its section 9; from 2.3.0 to 2.4.0: its section 10; from 2.4.0 to 2.5.0: its section 11.

| Module | Coordinates | What it is |
| --- | --- | --- |
| `platform-bom` | `com.itways:platform-bom` (pom) | The versions of every in-house library (`common-core`, `common-web`, `common-messaging`, `ai-engine-sdk`, `file-storage-sdk`, `journey-model`, `journey-engine-sdk`). |
| `platform-parent` | `com.itways:platform-parent` (pom) | The parent of every service and library: `spring-boot-starter-parent` 3.2.2, the imported BOMs (resilience4j, Spring Cloud, Testcontainers, AWS SDK, `platform-bom`), the pins Boot does not manage (jjwt, MapStruct, Lombok, springdoc, ArchUnit) and the plugins every build runs (JaCoCo, surefire, failsafe, sources jar). `spring-boot-maven-plugin` and Spotless are configured but not activated. |
| `common-core` | `com.itways:common-core` | Framework-free: the credential rules (`security.core`), the contracts services exchange, the response envelope, error codes and exceptions, the public-URL policy, UTC time handling. Depends on jjwt and Jackson annotations only. The api-gateway (WebFlux) uses this one. |
| `common-web` | `com.itways:common-web` | The Spring MVC side: security filters and stores, `@AccountId` and `@RequestedScope`, assistant scope rules, caching, encryption and secret sealing, the shared error handling, OpenAPI helpers, and the opt-in Feign, FreeMarker and JPA-auditing integrations. Depends on `common-core`. |
| `common-messaging` | `com.itways:common-messaging` | The RabbitMQ side: JSON conversion, publisher confirms and returns, the activity and notification publishers with their DTOs, the transactional activity outbox. Depends on `common-core`. |

`common-web` and `common-messaging` do not depend on each other; a service takes the
ones it needs. All five share one version (`2.5.0`). Java 21, Spring Boot 3.2.x.

The packages did not move with the split: `com.itways.*` names are the same as in
`common-lib` 1.x, only the jar that holds them changed. Two packages are spread over
two jars (`com.itways.annotation`, `com.itways.common`); that is fine on a classpath.

## Build

The library is not published to a remote repository. Install it into the local
Maven repository **before** building any service; the root pom aggregates the five
modules and builds them in order:

```bash
mvn -f common-lib/pom.xml install          # runs the tests; -DskipTests to skip them
```

Each service's CI installs it first, and the `libs` stage of the workspace's
`docker/java/Dockerfile` installs the reactor before the other libraries.
common-lib's own CI, `.github/workflows/ci.yml`, calls the platform's shared Java
pipeline (`java-build.yml` in the workspace repository, `itways-ai/platform-workspace`)
on every push and pull request: `mvn verify`, then `mvn spotless:check`. As a library it
publishes no image.
On a host whose JDK is newer than 21, Lombok fails with "cannot find symbol"; build
with JDK 21 (or in the `maven:3.9-eclipse-temurin-21` image).

`install` produces, per module, `<module>-<v>.jar` and `<module>-<v>-sources.jar`,
plus the two poms:

| Artifact | Used by |
| --- | --- |
| `common-core-<v>.jar` | every service, and the api-gateway (WebFlux) on its own |
| `common-web-<v>.jar` | account, auth, channels, journey, notification, conversation, template |
| `common-messaging-<v>.jar` | every service that publishes activity or notifications |
| `platform-parent-<v>.pom`, `platform-bom-<v>.pom` | every service's `<parent>`; the BOM through it |

The former `common-lib-<v>-security-core.jar` classifier is gone: `common-core` is
that jar, complete.

Formatting is not part of the build. `.editorconfig` states the rules (4 spaces, LF,
final newline); `mvn spotless:apply` applies them on demand (the plugin is configured
in `platform-parent`, not bound to any phase).

## Version policy

- The five modules share one version, and all services pin it through
  `platform-bom` (imported by `platform-parent`). A change that alters behaviour
  every service sees (a startup check, a new default) is announced in the open-points
  tracker and verified against every consumer's test suite before it is copied in.
- Every release bumps the version and git-tags the repository (`v<version>`); the
  new version goes into `platform-bom`, and the services move by updating their
  `platform-parent` version. Bump when a change breaks a consumer's compile or needs
  a consumer-side change.
- Keep `common-core` framework-free (`CommonCoreIsFrameworkFreeTest` checks every
  class of the module against the JDK, jjwt, Jackson annotations and the optional
  swagger annotations, and holds `com.itways.security.core` to the stricter rule: no
  Lombok, no logging, nothing but the JDK and jjwt). The gateway loads it without
  Spring MVC; the gateway's `CommonLibCompatibilityTest` checks that the edge and the
  services judge credentials alike.

## How a service opts in

Nothing is active just by being on the classpath except what is listed under
"Always on". The rest is switched on with an annotation on the application class.
Each configuration imports its classes explicitly (no component scan), under the
bean names the former scan gave (`EnableAnnotationsBeanNamesTest`).

| Annotation | Module | Imports | What the service gets |
| --- | --- | --- | --- |
| `@EnableCommon` | common-web | `common.config.CommonConfig` | `GlobalExceptionHandler`, `DataAccessExceptionHandler`, `CustomErrorController`, `SwaggerConfig` (OpenAPI schema helpers), `TimeConfig` (UTC), request correlation (`RequestCorrelationConfig`, the `requestIdFilter`; see "Request correlation") and, during the header rename, `LegacyAssistantHeaderConfig` (see "Assistant header"). `ApiResponse` / `PageResponse` are plain classes in common-core. |
| `@EnableRequestCorrelation` | common-web | `web.correlation.RequestCorrelationConfig` | The request-id filter alone, for a servlet service without `@EnableCommon` |
| `@EnableCustomSecurity` | common-web | `security.config.SecurityConfig` + `@EnableCache` | `JwtTokenProvider`, `JwtAuthenticationFilter`, `ApiKeyAuthenticationFilter`, `SecurityUtils`, `ApiKeyProvider`, the session-revocation and API-key allow-list stores, `@AccountId` resolver, `InternalServiceToken`, the shared 401/403 handlers, `ClientIpResolver` and `ServiceCalls` (ARC-11; beans only, nothing switched on); with `itways.internal-token` set, `ServiceTokenAuthenticationFilter` (2.2.0; a bean for the service's chain to add, never run by the container on its own, see "Service calls") |
| `@EnableInternalEndpointGuard` | common-web | `security.internal.InternalEndpointGuardConfig` | The `/internal/` guard filter (`internalEndpointGuard`, see "Shared helpers"); needs `@EnableCustomSecurity` |
| `@EnableMailSecrets` | common-web | `encryption.MailSecretsConfig` | The `MailSecrets` bean from `MAIL_SECRETS_KEY`, required unless `itways.mail-secrets.required=false` |
| `@EnableConnectorSecrets` | common-web | `encryption.ConnectorSecretsConfig` | The `ConnectorSecrets` bean from `CONNECTOR_SECRETS_KEY` (2.3.0; the credentials stored on connectors, journey-service only), required unless `itways.connector-secrets.required=false` |
| `@EnableCache` | common-web | `cache.config.CacheConfig` + `@EnableCaching` | `CacheStoreFactory` (Ehcache, Redis, hybrid) and the bounded `CacheManager` |
| `@EnableAssistantScope` | common-web | `scope.AssistantScopeConfig` | `AssistantDirectory`, `ScopeRules`, `@RequestedScope ListScope` parameters (needs a `JdbcTemplate`; spring-jdbc is optional here), and `LegacyAssistantHeaderConfig` like `@EnableCommon` |
| `@EnableEncryption` | common-web | `encryption.EncryptionConfig` | `RsaService` (the `EncryptionService`); `ChannelSecrets` and `MailSecrets` are static helpers of that package |
| `@EnableForwardedAuth` | common-web | `feign.ForwardedAuthFeignConfig` | Feign interceptor that forwards the caller's credential and `X-Service-Token` (needs Feign on the service's classpath; the configuration backs off without it) |
| `@EnableFreeMarker` | common-web | `freemarker.FreeMarkerConfig` | `TemplateRender` (needs `spring-boot-starter-freemarker` on the service's classpath; backs off without it) |
| `@EnableAccountAuditing` | common-web | `jpa.AccountAuditingConfig` | JPA auditing of the account id (needs Spring Data JPA on the service's classpath; backs off without it) |
| `@EnableActivity` | common-messaging | `activity.config.ActivityConfig` | `ActivityEventPublisher` (account activity over RabbitMQ); with `itways.activity.outbox.enabled=true` also `ActivityOutbox` and its relay (see "Activity outbox"; needs spring-jdbc and spring-tx, optional here) |
| `@EnableNotifications` | common-messaging | `notification.config.NotificationConfig` | `NotificationPublisher`, `notification.queue` (declared argument-free: every sender declares it) |

A service's own `SecurityFilterChain` decides who may call what; common-lib's filters
only establish who the caller is.

Not provided any more (since 2.0.0): the `restTemplate` bean of `@EnableCommon` (a
service that needs a `RestTemplate` declares its own) and `RefGenerator`. The
library's `application.properties` (RabbitMQ `guest` defaults, ANSI output) and
`banner.txt` are gone too; every service ships its own properties, and the banner
is now Spring Boot's.

### Always on (auto-configuration)

- common-web, `META-INF/spring/…AutoConfiguration.imports`:
  `cache.config.CacheAutoConfiguration` (the cache beans, ordered before Spring
  Boot's cache auto-configuration) and `common.config.SwaggerConfig`.
- common-web, `META-INF/spring.factories`: `security.config.GeneratedUserFilter`
  leaves out Spring Boot's `UserDetailsServiceAutoConfiguration`, see below;
  `common.diagnostics.DatabaseLoginFailureAnalyzer` (2.1.0) turns a start the
  database refused into "set `SPRING_DATASOURCE_PASSWORD`" (or "check the user
  name and password" when one is set), since the services ship no default
  database password.
- common-messaging, `AutoConfiguration.imports`:
  `messaging.RabbitPublishingAutoConfiguration` logs publisher nacks and returns,
  and carries the request id over RabbitMQ (ARC-25, see "Request correlation").
- common-messaging, `spring.factories`: `messaging.RabbitPublishingDefaults`
  (an `EnvironmentPostProcessor`) turns on correlated publisher confirms, returns
  and mandatory publishing for every service's RabbitMQ connection (PLT-30); a
  service's own setting wins.

## Packages

| Package | Module | What it holds |
| --- | --- | --- |
| `security.core` | core | Framework-free credential rules: `TokenVerifier` (JWT, platform and channel-webhook keys, rotation), `ApiKeyCodec` (`X-API-KEY` format), `CredentialCrypto` (AES-256-GCM, SHA-256), `PublicKeys`, `SecurityMessages`. |
| `contracts` | core | Payloads services exchange: `account`, `channels`, `journey`, `knowledge`, `template` (`TemplateVariable` keeps its `optional` flag and 3-argument constructor). Their `@Schema` descriptions feed the portal's OpenAPI specs; the annotation library is optional. `knowledge` also holds `KnowledgeIndexName`, the index-name rule (2.2.0). |
| `common.text` | core | `PiiScrubber` (e-mail addresses and phone numbers out of stored text) and `PassageHashes` (the knowledge passage and source hashes, equal to journey-service's SQL) (2.2.0). |
| `common.response`, `common.constants`, `common.exception` | core | Envelope (`ApiResponse`, `PageResponse`), error codes, `BusinessException`, `InvalidApiKeyException`. |
| `common.net` | core | `PublicUrlPolicy`: whether a tenant-supplied URL may be called (SSRF guard). `HostAllowList` (2.3.0): the one allow-list rule for hosts an operator lets through that guard (exact name, IP literal, `.domain` suffix; normalised matching; refuses entries that are not hosts), used by `PublicOnlyDnsResolver` and meant for `EgressGuard`, `TenantMailGuard` and the connectors' per-connector lists. `IpLiterals`, `TrustedProxies` and `ClientIp`: the client-IP rule behind our proxies (the gateway's, shared). |
| `common.util` | core | `UtcDateTimes`. |
| `common.correlation` | core | `RequestIds`: the request-id rule (names, well-formedness, accept or generate) the gateway and the services share. |
| `security`, `security.jwt`, `security.servlet` | web | The Spring side: `JwtTokenProvider`, `SecurityUtils`, `ApiKeyProvider`, `SessionRevocationStore`, `ApiKeyStatusStore`, the two servlet filters, `ApiResponseAuthenticationEntryPoint` / `ApiResponseAccessDeniedHandler`; `Sessions` (the USER_SESSION rule and the details keys) and `ClientIpResolver` (the servlet side of `ClientIp`). |
| `security.internal` | web | `InternalServiceToken`: recognises another platform service on `/internal/` routes (`X-Service-Token`, `itways.internal-token`). `InternalEndpointGuard` / `InternalEndpointGuardConfig`: the filter that keeps those routes off the public edge (`@EnableInternalEndpointGuard`). `ServiceTokenAuthenticationFilter` / `ServiceTokenAuthenticationConfig` (2.2.0): a session for a service call that carries only the token. |
| `web.client` | web | `ServiceCalls`, `ForwardedCallerInterceptor`, `CallerCredentials`: `RestClient`s to other platform services that carry the service token and the caller's credential. |
| `web.correlation` | web | `RequestIdFilter`, `CurrentRequestId`, `RequestCorrelationConfig`: the request id of the request being served. |
| `web.net` | web | `PinnedHttpClients`, `PublicOnlyDnsResolver`, `BoundedDownloads`: calls to tenant-supplied URLs pinned to vetted public addresses, and size-capped downloads (needs Apache HttpClient 5, optional here). |
| `security.config`, `security.resolver`, `security.annotation` | web | Security wiring, `@AccountId`. |
| `scope` | web | Per-assistant scoping: `AssistantScope`, `ScopeRules`, `ListScope`, `RequestedScopeArgumentResolver`, `ScopeHeaders`, `ScopeErrors`; `LegacyAssistantHeaderFilter` / `LegacyAssistantHeaderConfig` (the old header name, during the rename). |
| `cache` | web | `CacheStore` / `CacheStoreFactory` with Ehcache, Redis and hybrid stores; `CacheProperties`. |
| `encryption` | web | `SealedSecrets` (2.3.0): the one single-key AES-256-GCM cipher (`<prefix><kid>:` + Base64, a context per call, current + previous key, `seal` / `open` / `reseal` / `isCurrent`); its subclasses `ChannelSecrets` (`cs:`, `CHANNEL_SECRETS_KEY`: channel provider secrets), `MailSecrets` (`ms:`, `MAIL_SECRETS_KEY`: SEND_MAIL SMTP passwords) and `ConnectorSecrets` (`is:`, `CONNECTOR_SECRETS_KEY`: connector credentials, context `instanceId|accountId|field`); `EncryptionService`, `RsaService`. Stored `ms:` / `cs:` values are unchanged (`SealedSecretsGoldenVectorsTest`). |
| `common.diagnostics` | web | `DatabaseLoginFailureAnalyzer`: startup failure analysis for a refused database login. |
| `common.config`, `common.handler` | web | `CommonConfig`, `SwaggerConfig`, `TimeConfig`, the OpenAPI customizers; the exception handlers (`GlobalExceptionHandler` is an overridable base, see "Shared helpers") and `/error` controller. |
| `feign`, `freemarker`, `jpa` | web | The opt-in integrations above. |
| `annotation` | web + messaging | The `@Enable*` annotations; `EnableActivity` and `EnableNotifications` ship in common-messaging, the rest in common-web. |
| `amqp`, `messaging` | messaging | RabbitMQ JSON conversion; publisher confirms and returns; `DeadLetterQueueGauge` (a queue's depth as a Micrometer gauge). |
| `messaging.correlation` | messaging | `RequestIdPublishPostProcessor`, `RequestIdListenerAdvice`, `RequestIdListenerAdviceRegistrar`: the request id on published messages and in listeners. |
| `notification`, `activity` | messaging | Notification and activity publishers and their DTOs. |
| `activity.outbox` | messaging | The transactional outbox for activity events: `ActivityOutbox`, `ActivityOutboxRelay`, `ActivityOutboxStore`, `RabbitConfirmedSender`, the health indicator and gauges. |

## Shared helpers (ARC-11)

Eight helpers the services had copied from one another now have one version
here. A service that adopts one deletes its copy. Two things change on 2.0.0 for every
service with `@EnableCustomSecurity` or `@EnableCommon`, before it adopts anything:

- `@EnableCustomSecurity` registers the `clientIpResolver` bean. It reads
  `itways.client-ip.trusted-proxies` (default `TRUSTED_PROXIES`) at startup, and a host
  name there fails the startup. A service's own bean named `clientIpResolver` clashes
  with it (account-service's `activity/ClientIpResolver`): the service does not start
  until it deletes its copy in the same change.
- `@EnableCommon` registers the request-id filter: every response carries
  `X-Request-Id`, and error bodies carry `reference` (see "Request correlation").
  `itways.request-id.enabled=false` opts out.

Everything else here is opt-in. The rule of each helper is the strictest correct
one among the copies; where copies legitimately differed there is a knob.

| Helper | Module, package | How to enable | Replaces |
| --- | --- | --- | --- |
| `InternalEndpointGuard` | common-web, `security.internal` | `@EnableInternalEndpointGuard` on the application class (needs `@EnableCustomSecurity`) | account `web/InternalEndpointGuard`, channels / journey / template `config/InternalEndpointGuard` |
| `Sessions` | common-web, `security.servlet` | Use it: `anyRequest().access(Sessions.userSession())`, `Sessions.isUserSession(auth)`, `Sessions.kindOf(auth)` | account / channels `SecurityConfig.USER_SESSION`, journey `support/Sessions`, template `SecurityConfig.isUserSession`, conversation `chat/ChatOwner.isUserSession`, account `ActivityRecorder.currentCredentialKind` |
| `ClientIp` + `ClientIpResolver` | common-core `common.net` (`IpLiterals`, `TrustedProxies`, `ClientIp`); common-web `security.servlet.ClientIpResolver` | The bean `clientIpResolver` comes with `@EnableCustomSecurity`; `resolve(request)` or `current()` | gateway `net/IpLiterals`, `net/TrustedProxies` (the gateway keeps `ClientAddressFilter` on top of `ClientIp`), account `activity/ClientIpResolver`, auth `audit/ClientRequestInfo` (its IP part) |
| `GlobalExceptionHandler` (overridable) | common-web, `common.handler` | Already there with `@EnableCommon`; a service with its own codes registers `class XExceptionHandler extends GlobalExceptionHandler` (a scanned `@RestControllerAdvice`) and overrides handlers or hooks; the base backs off | account `web/AccountExceptionHandler`, auth `config/AuthExceptionHandler` (the framework part; their domain handlers stay in the subclass) |
| `ServiceCalls` | common-web, `web.client` | The bean `serviceCalls` comes with `@EnableCustomSecurity`: `serviceCalls.client(baseUrl)` / `.builder()`; or `.headers(serviceCalls::forwardCaller)` on a client built elsewhere | channels / journey `integrations/ServiceHttp`, account `assistants/ForwardedCaller`, template `integrations/JourneyUsageClient.callerAndServiceHeaders`; the Feign interceptor of `@EnableForwardedAuth` shares its resolution (`CallerCredentials`) |
| `MailSecretsConfig` | common-web, `encryption` | `@EnableMailSecrets`; consumers inject `MailSecrets` (or `ObjectProvider<MailSecrets>` when optional) | journey / notification `config/MailSecretsConfig`, conversation `notification/RabbitMailDeliveryAdapter`'s inline `new MailSecrets(...)` |
| `DeadLetterQueueGauge` | common-messaging, `messaging` | Declare a bean: `new DeadLetterQueueGauge(amqpAdmin, "notification.dlq.messages", "notification.dlq", "...")` (Actuator on the classpath; no `@EnableScheduling` needed) | account `activity/DeadLetterQueueGauge`, notification `messaging/DeadLetterQueueGauge` |
| `PinnedHttpClients`, `PublicOnlyDnsResolver`, `BoundedDownloads` | common-web, `web.net` | Use them; add `org.apache.httpcomponents.client5:httpclient5` to the service (optional here) | conversation `net/PinnedHttpClients`, `net/PublicOnlyDnsResolver`, `net/BoundedDownloads` |

The rules, and what changes for an adopter:

- **Internal guard.** An `/internal` path segment (regex `.*/internal(/.*|;.*)?`
  on the lower-cased raw URI and on the decoded servlet path, so `/%69nternal/`
  counts) that arrived through a proxy (any of `X-Forwarded-For`,
  `X-Forwarded-Host`, `Forwarded` present, whatever the value) is refused; a
  direct call is refused when `InternalServiceToken.admits` says so (log-only
  until `itways.internal-token-enforce=true`). The refusal is 404 with the
  real-404 body, `The requested resource was not found` / `NOT_FOUND`, the same
  one `GlobalExceptionHandler`, `CustomErrorController` and the gateway send, so
  a probe cannot tell the route exists. Channels, journey and template answered
  `Not found` with `NOT_FOUND` or `RES_001`: their proxied-probe body changes
  when they adopt. Filter name `internalEndpointGuard`, `/*`, order
  `HIGHEST_PRECEDENCE + 10` (the first slots stay free for the request-id
  filter of ARC-25). A committed response is left alone. `InternalServiceToken`
  also gained `isConfigured()`, `matches(presented)` (strict, constant-time,
  ignores the enforce flag; for account's deletion route and template's render
  lane) and `headerValue()` (what an outbound call sends). Static
  `isInternalPath(request)` and `cameThroughProxy(request)` are public.
- **User session.** A session is a user's when it is authenticated, names a
  principal (conversation's check), has a details map, is not an API key
  (`authSource=API_KEY`), is not a channel webhook token
  (`tokenType=CHANNEL_WEBHOOK`) and, if it names a token type at all, that type
  is `ACCESS` (auth's allow-list: a refresh token or an unknown type is refused).
  A details map without a token type still counts as a user, because five
  services and many test fixtures build one that way and the JWT filter never
  omits it. The details keys are constants (`Sessions.DETAIL_*`); the filters
  write the same strings as before.
- **Client IP.** The gateway's rule: every `X-Forwarded-For` line, comma-split,
  each hop normalised (trim, quotes, brackets, `:port`), a hop that is not an IP
  literal skipped and never resolved; the header consulted only when the peer is
  a trusted proxy; the right-most untrusted literal, else (all hops ours) the
  left-most, else the peer; cut to 64 characters. Account and auth read only the
  first header line and returned a garbage hop as the client: account's test
  `garbageInTheHeaderIsNotTreatedAsAProxy` now expects the peer instead of
  `evil.example.com`. Trusted proxies: `itways.client-ip.trusted-proxies`
  (default `${TRUSTED_PROXIES:127.0.0.0/8,::1/128,172.16.0.0/12}`); a name fails
  the startup, as in the gateway. Account and auth keep reading their own keys
  until they adopt.
- **Exception handler.** Defaults are unchanged. Hooks: `error(status, message,
  code)`, `validationFailed(map)`, `newReference()` (the request id, else a UUID; ARC-25),
  `clientErrorCode(status)` (`HTTP_<n>`; auth overrides to `HttpStatus.name()`),
  `hideServerErrorMessages()` (`itways.errors.hide-server-error-messages`,
  default `false`: when `true` a 5xx `BusinessException` answers its own status
  with `Internal server error (reference X)` / `INTERNAL_SERVER_ERROR` and the
  real message is logged with the reference; account and auth override it to
  `true`). The base is `@ConditionalOnMissingBean(GlobalExceptionHandler.class)`:
  a subclass that is a scanned component, or a configuration class registered
  before `@EnableCommon` is processed, replaces it; a `@Bean` method on the
  application class itself is registered too late and would run beside it.
- **Service calls.** Not a global `RestClientCustomizer`: the service token and
  the caller's credential must never reach Telegram, Twilio or an AI provider,
  so `ServiceCalls.builder()` clones Boot's builder (or starts a fresh one), adds
  `X-Service-Token` = `InternalServiceToken.headerValue()` when configured
  (trimmed; nothing when blank) and the `ForwardedCallerInterceptor`, which
  copies `Authorization` (or the `ForwardedAuthorizationResolver`'s answer) and
  `X-API-KEY` from the request being served, each only when present, nothing
  outside a request, never logged. `client(baseUrl)` uses the JDK factory with
  2 s connect / 5 s read. Channels' calls did not forward `X-API-KEY`; they do
  once they adopt.
- **Mail secrets.** `mail.secrets.key` (default `${MAIL_SECRETS_KEY:}`) and
  `mail.secrets.previous-key` (default `${MAIL_SECRETS_KEY_PREVIOUS:}`); a
  blank key fails the startup with `MailSecrets`' own message. With
  `itways.mail-secrets.required=false` a blank key registers no bean (conversation's
  optional use; a WARN says so). A service's own `MailSecrets` bean wins.
- **DLQ gauge.** `(AmqpAdmin, metricName, queueName, description)` plus an
  overload with `initialDelay` / `refreshInterval` (10 s / 30 s); its own daemon
  thread, started when the registry binds it, stopped on destroy; -1 while
  unknown; tag `queue=<queueName>` (new for the account and notification metrics:
  the series gains a label, the names stay); a failure logs the exception's
  class name only, at DEBUG.
- **Pinned HTTP.** `PublicOnlyDnsResolver` keeps conversation's signatures and refusal
  (`RefusedAddressException`, an `UnknownHostException`, message without the
  address) and gains an allow-list (exact names or `.domain` suffixes, as
  journey-engine's and notification's guards have) and a `Predicate<InetAddress>`
  for what counts as public (`PublicUrlPolicy::isPublic` by default).
  `PinnedHttpClients` keeps conversation's signatures, defaults (pool 25/5, the
  `followRedirects` parameter) and adds journey-engine's stricter settings:
  cookies are never kept and waiting for a pooled connection is bounded by the
  connect timeout; an overload takes a `PoolSize`. `BoundedDownloads` is as it
  was.

## Service calls (2.2.0)

A background job (conversation-service's knowledge ingestion worker) has no user's
token to forward. `ServiceTokenAuthenticationFilter` gives a call that carries the
platform's service token and nothing else a session of its own, and
`Sessions.serviceCall()` admits it where a chain says so:

- **Who gets it.** `X-Service-Token` equal to `itways.internal-token`
  (`InternalServiceToken.matches`: trimmed, constant-time), no `Authorization` and no
  `X-API-KEY` header, and no session set before. The session: principal
  `platform-service`, authority `SERVICE`, details `{authSource: SERVICE_TOKEN}`, no
  account. The Feign interceptor sends the token on every call, so a call made for a
  user carries both and stays the user's; with an invalid user token it stays
  unauthenticated (401), never a service call. A wrong token is logged (method and path,
  never the value) and the request goes on unauthenticated; the filter answers nothing.
- **Rules.** `Sessions.isServiceCall(auth)` / `serviceCall()`. `kindOf` says `NONE` for a
  service call, so `userSession()` refuses it; `authenticated()` admits it (and
  `@AccountId` is `null` for it), so put service-only rules before broader ones.
- **Wiring.** With `@EnableCustomSecurity` and a non-blank `itways.internal-token`, the
  bean `serviceTokenAuthenticationFilter` exists; its `FilterRegistrationBean` is
  disabled, so it runs only in a chain that adds it, after the JWT and API-key filters:
  `serviceTokenFilter.ifAvailable(f -> http.addFilterBefore(f, UsernamePasswordAuthenticationFilter.class))`
  written after their two `addFilterBefore` lines (inject
  `ObjectProvider<ServiceTokenAuthenticationFilter>`). `ServiceTokenAuthenticationFilter.serviceAuthentication()`
  builds the session for a service's security tests.

## Request correlation (ARC-25)

One id follows a user request through every service it touches, so the log lines
of all of them can be found together and a caller can quote the id of a failed
request. The rule is common-core's `RequestIds`: header `X-Request-Id`, logging
key `requestId`, AMQP header `x-request-id`; an incoming id is kept when it is 1
to 64 characters of `[A-Za-z0-9._-]` (after trimming), otherwise a new random
UUID is used, so a forged value cannot inject text into a log line.

How it flows: filter → MDC → RestClient / Feign header → AMQP header → listener
MDC → error envelope `reference`.

1. **Gateway.** The api-gateway (WebFlux) accepts or creates the id with
   `RequestIds.accept(...)` and sends it on to the service as `X-Request-Id`
   (GW-12, in the gateway's repository).
2. **Servlet filter.** `RequestIdFilter` applies the same rule to the incoming
   header, echoes the id as the response's `X-Request-Id` before the rest of the
   chain runs (so it is there however the response is committed), stores it as the
   request attribute `com.itways.requestId` and puts it in the SLF4J MDC as
   `requestId` for the duration of the request; the previous value is restored
   afterwards. It is registered as `requestIdFilter` on `/*` first of all filters
   (`Ordered.HIGHEST_PRECEDENCE`; the internal-endpoint guard is at `+10`, Spring
   Security at -100) for request, async and error dispatches; a re-dispatch keeps
   the id of the first pass. `CurrentRequestId.get()` reads the id.
3. **Outbound HTTP.** `ForwardedCallerInterceptor` (every `ServiceCalls` client)
   and the Feign interceptor of `@EnableForwardedAuth` send the current id as
   `X-Request-Id` unless the call names one already, also outside a request (a
   listener that has one).
4. **Publishing.** The `requestIdPublishing` customizer adds a
   `RequestIdPublishPostProcessor` to Spring Boot's `RabbitTemplate`: a message
   published while the MDC holds an id gets the `x-request-id` header (a header
   the message already has is kept). `NotificationPublisher` and the services' own
   publishers get it this way, with their calls unchanged. `AccountActivityEvent`
   gained `requestId` (left out of the JSON when null): `ActivityOutbox` fills it
   from the MDC when the caller did not, so the relay, which sends later on its own
   thread, still sets the header (`RabbitConfirmedSender`); `ActivityEventPublisher`
   sets the header from the event's `requestId` when present.
5. **Listeners.** `RequestIdListenerAdviceRegistrar` (a `BeanPostProcessor`) puts
   `RequestIdListenerAdvice` in front of the advice chain of every
   `AbstractRabbitListenerContainerFactory` bean (Boot's
   `rabbitListenerContainerFactory` and the services' own); the advice already
   there, such as Boot's retry interceptor, stays behind it, so retries and the
   recoverer's log lines carry the id as well. While a listener handles a message
   the MDC holds the message's `x-request-id` (the first message of a batch); a
   message without one, or with a malformed one, runs with the key removed, so an
   earlier id never leaks into it. A container built by hand adds the
   `requestIdListenerAdvice` bean to its own chain.
6. **Error envelopes.** `ApiResponse` gained `reference` (left out of the JSON
   when null, so success bodies and bodies written without an id keep exactly
   their five fields). Every error body the library writes sets it to the current
   id: `GlobalExceptionHandler` (the `error(...)` / `validationFailed(...)` hooks
   and the business and 405 answers), `DataAccessExceptionHandler`,
   `CustomErrorController` (falls back to the request attribute),
   `ApiResponseAuthenticationEntryPoint` / `ApiResponseAccessDeniedHandler`, the
   JWT filter's own 401 and `InternalEndpointGuard`'s 404. The reference quoted in
   a 500 (`Internal server error (reference X)`, also the hide-5xx answer and the
   409 data conflict) is the request id when there is one, a UUID otherwise. A
   subclass of `GlobalExceptionHandler` that overrides `error(...)` without
   calling `super` sets the field itself with `CurrentRequestId.stamp(body)`.

What a service does:

- Nothing to get the filter: `@EnableCommon` includes it (or add
  `@EnableRequestCorrelation`). The messaging side is on wherever common-messaging
  is on the classpath with Spring Boot's RabbitMQ auto-configuration.
- To show the id in the logs, set

  ```properties
  logging.pattern.level=%5p [${spring.application.name:-},%X{requestId:-}]
  ```

  Spring Boot's default console (and file) pattern prints the level through this
  property, so every line then shows the level followed by
  `[account-service,3f2a9c1e-...]` (nothing after the comma outside a request or
  message). No logback file is needed.

| Property | Default | Meaning |
| --- | --- | --- |
| `itways.request-id.enabled` | `true` | `false`: no `requestIdFilter` (no response header, no MDC key, so no `reference` in error bodies). |
| `itways.request-id.messaging.enabled` | `true` | `false`: no publish post-processor and no listener advice. The outbox still records an event's `requestId` and sends it as the header. |

What changes for a service that takes this version: every response carries
`X-Request-Id`, and error bodies carry `reference` whenever the request had an id,
which with the filter is always (MockMvc tests that register the service's
filters included). A test asserting that an error body has exactly five fields,
or no `reference`, needs updating; success bodies are unchanged. Outbox payloads
of events recorded during a request gain `requestId`; a consumer on an older
common-lib ignores it (Spring AMQP's JSON converter does not fail on unknown
properties). The OpenAPI schema of the envelope gains the optional `reference`.

## Assistant header (2.1.0)

The console's selected assistant travels in `X-Assistant-Id`
(`ScopeHeaders.ASSISTANT`). Its name before 2.1.0, `X-Nibras-Assistant`
(`ScopeHeaders.LEGACY_ASSISTANT`, deprecated for removal), is still accepted while
the portal and the services move over:

- `LegacyAssistantHeaderFilter` (filter `legacyAssistantHeaderFilter`, `/*`, order
  `HIGHEST_PRECEDENCE + 1`, after the request id and before the internal guard and
  Spring Security) hands a request that sends only the legacy name to the service
  as if it had sent `X-Assistant-Id` with the same value. A controller parameter
  `@RequestHeader(ScopeHeaders.ASSISTANT)` therefore accepts both names. It comes
  with `@EnableCommon` and with `@EnableAssistantScope` (once when both are on).
- `@RequestedScope` reads `X-Assistant-Id`, else the legacy name, even without the
  filter. When both are sent, `X-Assistant-Id` wins.
- A request that used the legacy name is logged at DEBUG
  (`com.itways.scope.AssistantHeader`), so the last callers can be found.
- Nothing in common-lib sends the header; a service's own outbound calls use
  `ScopeHeaders.ASSISTANT`.

Removal: once no caller sends the legacy name, delete `LEGACY_ASSISTANT`,
`LegacyAssistantHeaderFilter` and `LegacyAssistantHeaderConfig` in a major release.

### The Shared workspace (2.4.0)

The console can select a virtual *Shared* workspace: no row, no migration, just a
name for the existing shared scope (rows with a null `assistant_id`). It travels
as the header value `shared` (`ScopeHeaders.SHARED_VALUE`).

- `SelectedScope` (`ASSISTANT` | `SHARED` | `NONE`) is what the header selects;
  `AssistantHeader.read(request::getHeader)` (public since 2.4.0) or
  `SelectedScope.parse(String)` reads it, 400 `INVALID_SCOPE` for anything else.
  `AssistantHeader.raw` is the value as sent. A controller that still binds the
  header as `@RequestHeader UUID` answers 400 to `shared`: bind it as a `String`
  and parse.
- `@RequestedScope`: the header `shared` lists shared only, like `scope=shared`;
  the `scope` parameter still wins (`SelectedScope.asListScope()`).
- `ScopeRules.forCreate(UUID, Boolean, SelectedScope, String)`: the Shared
  workspace selected makes a new row shared unless the request names an
  assistant. `ownerForCreate(UUID, Boolean, SelectedScope, String, String)` (rows
  that are never shared, channels) refuses it with 400 `SCOPE_REQUIRED` "pick an
  owner workspace" (`ScopeErrors.ownerRequired`), not `SHARED_NOT_ALLOWED`: the
  caller did not ask for sharing. The `UUID selected` overloads stay and mean
  `SelectedScope.of(uuid)`; a literal `null` or an untyped Mockito `any()` in that
  position is now ambiguous, so type it (`(UUID) null`, `any(UUID.class)`).
- `LegacyAssistantHeaderFilter` copies the value as sent, `shared` included.

### Names that keep the old product prefix

Data identifiers are not renamed with the code: a rename would sign every user out
or orphan stored entries. These Redis keys keep their `nibras:` prefix until the
product name is settled (BRD-03):

| Key | Written by | Read by |
| --- | --- | --- |
| `nibras:auth:revoked-session:<sid>`, `nibras:auth:pwchanged:<accountId>` | auth-service (`SessionRevocationStore`) | every service's `JwtAuthenticationFilter` |
| `nibras:apikeys:active:<keyHash>`, `nibras:apikeys:lastused:<keyHash>` | account-service (`ApiKeyStatusStore`) | every service's `ApiKeyAuthenticationFilter` |
| `nibras:cache:<cacheName>:<key>` | `RedisStore` (the Redis and hybrid caches) | the same |

`nibras:apikeys:revoked:*` (the deny-list before AS-08) is no longer read or written
since 2.1.0; leftover entries are inert and can be deleted (some have no TTL). No RabbitMQ exchange or
queue declared here carries the prefix.

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
and refused every real token (CH-15). Compose passes the public keys to the gateway and
every service that checks credentials (`x-credential-check-env` in the workspace's
`docker-compose.yml`), and the private key to auth-service (and to account-service,
which still reads provider keys stored under it). notification-service has no
`JwtTokenProvider` and gets none of them.

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
`GeneratedUserFilter` leaves that auto-configuration out wherever common-web is on the
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
| `itways.internal-token`, `itways.internal-token-enforce` | none, `false` | `X-Service-Token` for internal routes. A non-blank token also registers `serviceTokenAuthenticationFilter` (2.2.0). |
| `itways.internal-guard.enabled` | `true` | With `@EnableInternalEndpointGuard`: `false` registers no filter. |
| `itways.internal-guard.proxy-headers` | `X-Forwarded-For,X-Forwarded-Host,Forwarded` | The headers whose presence marks a proxied request (at least one). |
| `itways.client-ip.trusted-proxies` | `${TRUSTED_PROXIES:127.0.0.0/8,::1/128,172.16.0.0/12}` | The proxies allowed to say who the client is (`ClientIpResolver`); IP literals and CIDR ranges only. |
| `itways.request-id.enabled` | `true` | With `@EnableCommon` or `@EnableRequestCorrelation`: `false` registers no request-id filter. |
| `itways.request-id.messaging.enabled` | `true` | `false`: messages get no `x-request-id` header from the logging context and listeners do not read it. |
| `itways.errors.hide-server-error-messages` | `false` | `true` answers a 5xx `BusinessException` with a fixed text and a reference instead of its message. |
| `mail.secrets.key`, `mail.secrets.previous-key` | `${MAIL_SECRETS_KEY:}`, `${MAIL_SECRETS_KEY_PREVIOUS:}` | With `@EnableMailSecrets`: the key that seals SEND_MAIL passwords, and the retired one during a rotation. |
| `itways.mail-secrets.required` | `true` | `false`: a blank key registers no `MailSecrets` bean instead of failing the startup. |
| `connector.secrets.key`, `connector.secrets.previous-key` | `${CONNECTOR_SECRETS_KEY:}`, `${CONNECTOR_SECRETS_KEY_PREVIOUS:}` | With `@EnableConnectorSecrets` (2.3.0; renamed in 2.5.0): the key that seals the credentials stored on connectors, and the retired one during a rotation. |
| `itways.connector-secrets.required` | `true` | `false`: a blank key registers no `ConnectorSecrets` bean instead of failing the startup. |
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

`mvn test` runs each module's unit tests, among them:

- common-core: `CommonCoreIsFrameworkFreeTest` (every class of the module against
  the allowed references; `security.core` against the stricter rule);
  `TokenVerifierTest`, `ApiKeyCodecTest`, `CredentialCryptoTest`, `PublicUrlPolicyTest`,
  `TrustedProxiesTest`, `ClientIpTest`, `RequestIdsTest`; 2.2.0: `KnowledgeIndexNameTest`
  (the name rule table, normalising, case twins, legacy and storable names, slugs),
  `PiiScrubberTest` (e-mail addresses, phone numbers in ASCII, Arabic-Indic and Eastern
  Arabic-Indic digits, `+`/`00` forms, numbers in Arabic text, what is kept),
  `PassageHashesTest` (values computed by PostgreSQL 16 with the V14 formula),
  `KnowledgeContractsCompatibilityTest` (the 2.1.0 constructors and the new helpers);
  2.3.0: `HostAllowListTest` (exact, IP-literal and `.domain` entries in every spelling:
  case, trailing dot, brackets, IDN, IPv6 compression; numeric shorthand, `user@host`,
  ports and paths never match; entries that are not hosts are refused; the three
  existing settings keep their meaning).
- common-web: `SecurityCoreDelegationTest` (the Spring classes delegate to the
  credential rules); `JwtAuthenticationFilterTest` (who gets a session, who gets
  401, who continues unauthenticated); `AccessTokenRevocationTest` (the filter with
  the real `SessionRevocationStore` on a mocked Redis); `JwtTokenProviderStartupTest`
  (missing or broken keys fail the startup); `EnableAnnotationsBeanNamesTest` (the
  `@Enable*` imports keep the bean names of the former component scans);
  `SecurityErrorAnswersTest`, `GeneratedUserFilterTest`, `DefaultCacheManagerTest`,
  `JwtTokenProviderWebhookRotationTest`, `ScopeRulesTest`, `ListScopeTest`,
  `ChannelSecretsTest`, `MailSecretsTest`,
  `ForwardedAuthFeignConfigTest`; the shared helpers (ARC-11):
  `InternalEndpointGuardTest`, `InternalEndpointGuardConfigTest`,
  `InternalServiceTokenTest`, `SessionsTest`, `ClientIpResolverTest`,
  `GlobalExceptionHandlerTest` (every handler called directly, the hooks, the hide
  flag, a subclass replacing the base), `ServiceCallsTest` (against a local
  server), `MailSecretsConfigTest`, `PublicOnlyDnsResolverTest`,
  the request id (ARC-25): `RequestIdFilterTest`, `CurrentRequestIdTest`,
  `RequestCorrelationConfigTest` (registration and the servlet path through
  MockMvc), `ErrorEnvelopeReferenceTest` (every error writer, with and without an
  id), `ApiResponseJsonTest`,
  `PinnedHttpClientsTest`, `BoundedDownloadsTest`, `DnsRebindingTest` (needs
  `127.0.0.2` on the loopback interface, as on Linux; skipped elsewhere);
  2.1.0: `RequestedScopeArgumentResolverTest` and `LegacyAssistantHeaderFilterTest`
  (both header names, precedence, registration), `DatabaseLoginFailureAnalyzerTest`;
  2.2.0: `ServiceTokenAuthenticationFilterTest` (valid, trimmed, missing, wrong, a
  tenant credential beside the token, an existing session, and a timing check that a
  one-character guess costs as much as a nearly right one on a 1 MB token),
  `ServiceTokenAuthenticationConfigTest` (bean only with a non-blank token, disabled
  registration), `SessionsTest` (service calls), `KnowledgeContractsJsonTest` (2.1.0
  payloads into the new records and back, the `KnowledgeSource` aliases, round trips);
  2.3.0: `SealedSecretsTest` (format and key id, any text, another context, tampered
  or truncated values, another prefix, unknown key, rotation and reseal, key and
  prefix checks, `context`, the three kinds stay apart), `SealedSecretsGoldenVectorsTest`
  (values sealed by the 2.2.0 `MailSecrets` and `ChannelSecrets`, from the committed
  fixture `encryption/sealed-secrets-2.2.0.properties`, open with the 2.3.0 classes;
  same wire format and associated data byte for byte), `ConnectorSecretsTest` (per-row
  context: moved to another row, field or account it does not open; no legacy plain
  values), `ConnectorSecretsConfigTest` (the `@EnableConnectorSecrets` wiring, as
  `MailSecretsConfigTest`, and both beans side by side).
- common-messaging: `ActivityOutboxTest`, `ActivityOutboxRelayTest`,
  `ActivityOutboxConfigTest` (the outbox's transaction rules, retries and backoff,
  health and gauges, and that it stays off without the property);
  `RabbitPublishingAutoConfigurationTest`, `NotificationPublisherTest`,
  `DeadLetterQueueGaugeTest`; the request id (ARC-25):
  `RequestIdPublishPostProcessorTest`, `RequestIdListenerAdviceTest`,
  `RequestIdListenerAdviceRegistrarTest`, `ActivityEventPublisherTest`.

`mvn verify` also runs common-messaging's `ActivityOutboxIT` (Testcontainers:
Postgres and RabbitMQ): a rollback writes and sends nothing, a commit sends exactly
once, a broker outage keeps the rows and sends them after recovery, two relays never
send one row twice, sent rows go after the retention, and the request id recorded
with an event arrives as its `x-request-id` header.

Test counts at 2.3.0 (`mvn install`; the ITs are unchanged since 2.1.0):

| Module | Unit tests (surefire) | Integration tests (failsafe) |
| --- | --- | --- |
| common-core | 271 (179 at 2.2.0, 60 at 2.1.0): 2.3.0 adds the 84 of `HostAllowListTest`; the other 8 came with the knowledge-contract additions still in the working tree | none |
| common-web | 286 (248 at 2.2.0, 223 at 2.1.0), of which the 3 of `DnsRebindingTest` are skipped where `127.0.0.2` is not on the loopback interface (macOS): 2.3.0 adds 32 (`SealedSecretsTest` 12, `SealedSecretsGoldenVectorsTest` 4, `ConnectorSecretsTest` 8, `ConnectorSecretsConfigTest` 8); the other 6 came with the knowledge-contract additions | none |
| common-messaging | 58 | 10 (`ActivityOutboxIT`) |

625 in all (495 at 2.2.0, 351 at 2.1.0, 332 at 2.0.0).

A change here is verified against every consumer's own suite before it ships.
