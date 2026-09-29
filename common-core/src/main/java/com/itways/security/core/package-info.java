/**
 * The platform's credential rules, with no framework in them: JWT verification
 * ({@link com.itways.security.core.TokenVerifier}), the {@code X-API-KEY}
 * format ({@link com.itways.security.core.ApiKeyCodec}), the AES-256-GCM and
 * SHA-256 primitives behind both ({@link com.itways.security.core.CredentialCrypto})
 * and public-key parsing ({@link com.itways.security.core.PublicKeys}).
 *
 * <p>
 * common-lib's Spring classes ({@code JwtTokenProvider}, {@code SecurityUtils},
 * {@code ApiKeyProvider} and the servlet filters) delegate here, and so does
 * the reactive api-gateway, so the edge and the services judge a credential
 * with the same code.
 *
 * <p>
 * <b>Rules for this package</b>: plain Java plus jjwt only. No Spring, no
 * servlet, no Lombok, no logging of credential material, and no reference to
 * any other common-lib package except the constants class
 * {@code com.itways.contracts.channels.ChannelWebhookTokenClaims}. The package
 * ships in common-core, the framework-free module every consumer, the gateway
 * included, can depend on: common-web would bring Spring MVC, springdoc and
 * the auto-configurations into a WebFlux application.
 */
package com.itways.security.core;
