package com.itways.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itways.contracts.channels.ChannelWebhookTokenClaims;
import com.itways.security.SecurityUtils;
import com.itways.security.SessionRevocationStore;
import com.itways.security.jwt.JwtTokenProvider;

import io.jsonwebtoken.Jwts;

/**
 * The decisions of the JWT filter every service runs: who gets a session, who
 * is turned away with 401, and who simply continues unauthenticated (so the
 * service's entry point answers).
 */
class JwtAuthenticationFilterTest {

	private static final KeyPair PLATFORM = rsa();
	private static final KeyPair STRANGER = rsa();
	private static final String ACCOUNT_ID = "4242";

	private final SessionRevocationStore revocations = mock(SessionRevocationStore.class);
	private JwtTokenProvider tokens;
	private JwtAuthenticationFilter filter;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		new SecurityUtils().setEncryptionKey("jwt-filter-test-secret");
		tokens = new JwtTokenProvider();
		ReflectionTestUtils.setField(tokens, "publicKeyStr", encode(PLATFORM.getPublic().getEncoded()));
		ReflectionTestUtils.setField(tokens, "privateKeyStr", encode(PLATFORM.getPrivate().getEncoded()));
		ReflectionTestUtils.setField(tokens, "accessExpiration", 60_000L);
		ReflectionTestUtils.setField(tokens, "refreshExpiration", 60_000L);
		tokens.init();
		ObjectProvider<ObjectMapper> mappers = mock(ObjectProvider.class);
		when(mappers.getIfUnique(any())).thenAnswer(call -> ((Supplier<ObjectMapper>) call.getArgument(0)).get());
		filter = new JwtAuthenticationFilter(tokens, revocations, mappers);
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void aValidAccessTokenGetsASessionForItsAccount() throws Exception {
		Outcome outcome = run(accessToken(ACCOUNT_ID));

		assertThat(outcome.chainCalled()).isTrue();
		assertThat(outcome.authentication()).isNotNull();
		assertThat(outcome.authentication().getName()).isEqualTo("user@example.test");
		assertThat(details(outcome)).containsEntry("accountId", ACCOUNT_ID)
				.containsEntry("tokenType", JwtTokenProvider.TYPE_ACCESS);
	}

	@Test
	void noOrForeignOrMalformedTokensContinueUnauthenticated() throws Exception {
		String foreign = Jwts.builder().subject("x").claim("accH", SecurityUtils.hash(ACCOUNT_ID))
				.claim("accE", SecurityUtils.encrypt(ACCOUNT_ID))
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(STRANGER.getPrivate(), Jwts.SIG.RS256).compact();

		for (String header : new String[] { null, "Bearer ", "Bearer a.b.c", "Bearer " + foreign,
				"Basic " + accessToken(ACCOUNT_ID) }) {
			SecurityContextHolder.clearContext();
			MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
			if (header != null) {
				request.addHeader("Authorization", header);
			}
			MockFilterChain chain = new MockFilterChain();
			MockHttpServletResponse response = new MockHttpServletResponse();

			filter.doFilter(request, response, chain);

			assertThat(chain.getRequest()).as(String.valueOf(header)).isNotNull();
			assertThat(response.getStatus()).isEqualTo(200);
			assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		}
	}

	@Test
	void aRefreshTokenIsRefusedWith401() throws Exception {
		Outcome outcome = run(tokens.generateRefreshToken("user@example.test"));

		assertThat(outcome.chainCalled()).isFalse();
		assertThat(outcome.response().getStatus()).isEqualTo(401);
		assertThat(outcome.body().get("errorCode").asText()).isEqualTo("AUTH_401");
		assertThat(outcome.authentication()).isNull();
	}

	@Test
	void aBrokenTenantBindingIsRefusedWith401() throws Exception {
		String mismatched = tokens.generateAccessToken("user@example.test",
				Map.of("accH", SecurityUtils.hash("other"), "accE", SecurityUtils.encrypt(ACCOUNT_ID)));
		String unbound = tokens.generateAccessToken("user@example.test", Map.of("role", "USER"));

		for (String token : new String[] { mismatched, unbound }) {
			Outcome outcome = run(token);

			assertThat(outcome.chainCalled()).isFalse();
			assertThat(outcome.response().getStatus()).isEqualTo(401);
			assertThat(outcome.body().get("message").asText()).contains("tenant binding");
		}
	}

	@Test
	void aRevokedAccessTokenGetsTheShared401() throws Exception {
		when(revocations.isRevoked(anyString(), any(), any())).thenReturn(true);

		Outcome outcome = run(accessToken(ACCOUNT_ID));

		assertThat(outcome.chainCalled()).isFalse();
		assertThat(outcome.response().getStatus()).isEqualTo(401);
		assertThat(outcome.body().get("errorCode").asText()).isEqualTo("AUTH_401");
		assertThat(outcome.body().get("message").asText())
				.isEqualTo(JwtAuthenticationFilter.SESSION_ENDED_MESSAGE);
		assertThat(outcome.authentication()).isNull();
	}

	@Test
	void theSessionIdAndIssuedAtOfTheTokenAreWhatIsChecked() throws Exception {
		String withSid = tokens.generateAccessToken("user@example.test", Map.of("role", "USER",
				"accH", SecurityUtils.hash(ACCOUNT_ID), "accE", SecurityUtils.encrypt(ACCOUNT_ID),
				JwtTokenProvider.CLAIM_SESSION_ID, "sid-7"));

		assertThat(run(withSid).chainCalled()).isTrue();
		verify(revocations).isRevoked(eq(ACCOUNT_ID), notNull(), eq("sid-7"));

		// Minted before access tokens carried a sid: judged by the account cut-off only.
		assertThat(run(accessToken(ACCOUNT_ID)).chainCalled()).isTrue();
		verify(revocations).isRevoked(eq(ACCOUNT_ID), notNull(), isNull());
	}

	@Test
	void aWebhookTokenIsNotTiedToThePassword() throws Exception {
		when(revocations.isRevoked(anyString(), any(), any())).thenReturn(true);
		String webhook = Jwts.builder().subject("channel:c1")
				.claim(ChannelWebhookTokenClaims.CLAIM_TYPE, ChannelWebhookTokenClaims.TYPE_CHANNEL_WEBHOOK)
				.claim("accH", SecurityUtils.hash(ACCOUNT_ID)).claim("accE", SecurityUtils.encrypt(ACCOUNT_ID))
				.issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(PLATFORM.getPrivate(), Jwts.SIG.RS256).compact();

		Outcome outcome = run(webhook);

		assertThat(outcome.chainCalled()).isTrue();
		assertThat(details(outcome)).containsEntry("tokenType",
				ChannelWebhookTokenClaims.TYPE_CHANNEL_WEBHOOK);
		verify(revocations, never()).isRevoked(anyString(), any(), any());
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> details(Outcome outcome) {
		return (Map<String, String>) outcome.authentication().getDetails();
	}

	private record Outcome(boolean chainCalled, MockHttpServletResponse response, Authentication authentication) {
		JsonNode body() throws Exception {
			return new ObjectMapper().readTree(response.getContentAsString());
		}
	}

	private Outcome run(String token) throws Exception {
		SecurityContextHolder.clearContext();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
		request.addHeader("Authorization", "Bearer " + token);
		MockFilterChain chain = new MockFilterChain();
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, chain);
		return new Outcome(chain.getRequest() != null, response,
				SecurityContextHolder.getContext().getAuthentication());
	}

	private String accessToken(String accountId) {
		return tokens.generateAccessToken("user@example.test", Map.of("role", "USER", "accH",
				SecurityUtils.hash(accountId), "accE", SecurityUtils.encrypt(accountId)));
	}

	private static String encode(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	private static KeyPair rsa() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
