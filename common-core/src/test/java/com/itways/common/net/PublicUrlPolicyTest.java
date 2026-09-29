package com.itways.common.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * The lenient check ({@code problem}) lets a name that does not resolve through,
 * for callers that must not depend on DNS; the strict one ({@code strictProblem},
 * used when a person saves a setting) and the call-time one
 * ({@code requirePublic}) refuse it (SPC-03).
 */
class PublicUrlPolicyTest {

	private static final String URL = "https://identity.example.com/exchange";

	private static final PublicUrlPolicy.Resolver UNRESOLVABLE = host -> {
		throw new UnknownHostException(host);
	};

	private static PublicUrlPolicy.Resolver answering(String... addresses) {
		return host -> {
			InetAddress[] result = new InetAddress[addresses.length];
			for (int i = 0; i < addresses.length; i++) {
				result[i] = InetAddress.getByName(addresses[i]);
			}
			return result;
		};
	}

	@Test
	void theLenientCheckLetsAnUnresolvableNameThrough() {
		assertThat(PublicUrlPolicy.problem(URL, UNRESOLVABLE)).isEmpty();
	}

	@Test
	void theStrictCheckRefusesAnUnresolvableName() {
		assertThat(PublicUrlPolicy.strictProblem(URL, UNRESOLVABLE))
				.hasValueSatisfying(problem -> assertThat(problem).contains("does not resolve")
						.contains("identity.example.com"));
		assertThatThrownBy(() -> PublicUrlPolicy.requirePublic(URL, UNRESOLVABLE))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("host does not resolve");
	}

	@Test
	void bothChecksAgreeOnEverythingElse() {
		for (PublicUrlPolicy.Resolver resolver : new PublicUrlPolicy.Resolver[] { answering("93.184.216.34"),
				answering("93.184.216.34", "10.0.0.7"), answering("127.0.0.1") }) {
			assertThat(PublicUrlPolicy.strictProblem(URL, resolver)).isEqualTo(PublicUrlPolicy.problem(URL, resolver));
		}
		assertThat(PublicUrlPolicy.strictProblem(URL, answering("93.184.216.34"))).isEmpty();
		assertThat(PublicUrlPolicy.strictProblem(URL, answering("93.184.216.34", "10.0.0.7")))
				.hasValueSatisfying(problem -> assertThat(problem).contains("private address"));
		assertThat(PublicUrlPolicy.strictProblem("http://identity.example.com/", answering("93.184.216.34")))
				.hasValue("must use https");
		// A literal needs no lookup, so an unreachable resolver does not matter.
		assertThat(PublicUrlPolicy.strictProblem("https://93.184.216.34/x", UNRESOLVABLE)).isEmpty();
		assertThat(PublicUrlPolicy.strictProblem("https://169.254.169.254/x", UNRESOLVABLE)).isPresent();
	}

	@Test
	void theCallTimeCheckResolvesOnce() {
		AtomicInteger lookups = new AtomicInteger();
		PublicUrlPolicy.Resolver counting = host -> {
			lookups.incrementAndGet();
			return new InetAddress[] { InetAddress.getByName("93.184.216.34") };
		};

		assertThatCode(() -> PublicUrlPolicy.requirePublic(URL, counting)).doesNotThrowAnyException();
		assertThat(lookups).hasValue(1);
	}
}
