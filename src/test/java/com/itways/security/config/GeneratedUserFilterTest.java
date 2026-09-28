package com.itways.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.config.annotation.authentication.configuration.EnableGlobalAuthentication;
import org.springframework.security.core.userdetails.UserDetailsService;

/** PLT-20: no generated "user" and no "Using generated security password" line. */
class GeneratedUserFilterTest {

	private static final String[] CANDIDATES = { "a.Some", GeneratedUserFilter.USER_DETAILS_AUTO_CONFIGURATION, null,
			"b.Other" };

	@Test
	void onlyTheUserDetailsAutoConfigurationIsLeftOut() {
		GeneratedUserFilter filter = new GeneratedUserFilter();
		filter.setEnvironment(new MockEnvironment());

		assertThat(filter.match(CANDIDATES, null)).containsExactly(true, false, true, true);
		assertThat(GeneratedUserFilter.USER_DETAILS_AUTO_CONFIGURATION)
				.isEqualTo(UserDetailsServiceAutoConfiguration.class.getName());
	}

	@Test
	void aServiceCanTakeItBack() {
		GeneratedUserFilter filter = new GeneratedUserFilter();
		filter.setEnvironment(new MockEnvironment().withProperty(GeneratedUserFilter.ENABLED_PROPERTY, "true"));

		assertThat(filter.match(CANDIDATES, null)).containsExactly(true, true, true, true);
	}

	@Test
	void itIsRegisteredForEveryApplication() {
		assertThat(SpringFactoriesLoader.loadFactories(AutoConfigurationImportFilter.class, getClass().getClassLoader()))
				.hasAtLeastOneElementOfType(GeneratedUserFilter.class);
	}

	/** Through Spring Boot's own import machinery, as @SpringBootApplication and @WebMvcTest use it. */
	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void aServletApplicationGetsNoInMemoryUser(CapturedOutput output) {
		new WebApplicationContextRunner().withUserConfiguration(SecuredApp.class)
				.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(UserDetailsService.class));
		assertThat(output).doesNotContain("Using generated security password");

		// The control: the same application with the opt-out gets Boot's user back.
		new WebApplicationContextRunner().withUserConfiguration(SecuredApp.class)
				.withPropertyValues(GeneratedUserFilter.ENABLED_PROPERTY + "=true")
				.run(context -> assertThat(context).hasSingleBean(UserDetailsService.class));
		assertThat(output).contains("Using generated security password");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableGlobalAuthentication
	@EnableConfigurationProperties(SecurityProperties.class)
	@ImportAutoConfiguration(UserDetailsServiceAutoConfiguration.class)
	static class SecuredApp {
	}
}
