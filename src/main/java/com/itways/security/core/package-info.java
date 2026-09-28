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
 * {@code com.itways.contracts.channels.ChannelWebhookTokenClaims}. The build
 * packages this package (and that one class) on its own as the
 * {@code security-core} classifier jar, which is what the gateway depends on:
 * the full common-lib jar would bring Spring MVC, springdoc and AMQP, its
 * auto-configurations and its {@code application.properties} into a WebFlux
 * application.
 */
package com.itways.security.core;
