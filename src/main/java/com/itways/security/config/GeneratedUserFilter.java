package com.itways.security.config;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Leaves out Spring Boot's {@code UserDetailsServiceAutoConfiguration} in every
 * service that has common-lib on its classpath (PLT-20).
 *
 * <p>
 * That auto-configuration creates an in-memory user named {@code user} and logs
 * "Using generated security password: ..." at startup whenever a service
 * declares no {@code UserDetailsService} of its own, which is every platform
 * service: they authenticate with common-lib's JWT and API-key filters and never
 * with a username and password. The line put a working credential in the logs
 * (unused, since form login and HTTP Basic are off, but still a credential) and
 * the in-memory user was one more thing to reason about. account, auth, channels,
 * notification and template excluded it one by one; journey and speech did not.
 *
 * <p>
 * Registered in {@code META-INF/spring.factories}, so it applies to
 * {@code @SpringBootApplication} and to test slices ({@code @WebMvcTest}) alike;
 * a service's own {@code exclude} of the same class keeps working. A service
 * that ever needs Boot's in-memory user back sets
 * {@code itways.security.default-user.enabled=true}.
 */
public class GeneratedUserFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    /** The auto-configuration left out. */
    static final String USER_DETAILS_AUTO_CONFIGURATION =
            "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration";

    /** Set to {@code true} to keep Boot's in-memory user and its generated password. */
    public static final String ENABLED_PROPERTY = "itways.security.default-user.enabled";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean keep = environment != null && environment.getProperty(ENABLED_PROPERTY, Boolean.class, false);
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            matches[i] = keep || !USER_DETAILS_AUTO_CONFIGURATION.equals(autoConfigurationClasses[i]);
        }
        return matches;
    }
}
