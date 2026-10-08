package com.itways.common.diagnostics;

import java.sql.SQLException;
import java.util.Locale;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Names the setting to fix when a service does not start because the database
 * refused its login. The services ship no default database password (2.1.0), so a
 * deployment that forgets {@code SPRING_DATASOURCE_PASSWORD} fails at the first
 * connection (Flyway or the pool, during startup); without this analyzer the
 * startup report is a driver stack trace that does not say which variable is
 * missing.
 *
 * <p>
 * Recognised in the failure's cause chain: a {@link SQLException} with SQL state
 * {@code 28P01} (wrong password) or {@code 28000} (login refused, e.g. an unknown
 * role), or PgJDBC's {@code 08004} "no password was provided". Anything else is
 * left to the other analyzers ({@code null}).
 *
 * <p>
 * Registered in {@code META-INF/spring.factories}; Spring Boot passes the
 * application's {@link Environment}.
 */
public class DatabaseLoginFailureAnalyzer implements FailureAnalyzer {

    static final String PASSWORD_PROPERTY = "spring.datasource.password";
    static final String PASSWORD_ENV = "SPRING_DATASOURCE_PASSWORD";
    static final String USERNAME_ENV = "SPRING_DATASOURCE_USERNAME";

    private final Environment environment;

    public DatabaseLoginFailureAnalyzer(Environment environment) {
        this.environment = environment;
    }

    @Override
    public FailureAnalysis analyze(Throwable failure) {
        SQLException refused = loginRefusal(failure);
        if (refused == null) {
            return null;
        }
        if (noPasswordSent(refused) || !passwordConfigured()) {
            return new FailureAnalysis(
                    "The database asked for a password and this service has none: " + PASSWORD_PROPERTY
                            + " is not set. (" + refused.getMessage() + ")",
                    "Set " + PASSWORD_ENV + ". The services have no default database password, so every deployment"
                            + " must provide it.",
                    failure);
        }
        return new FailureAnalysis("The database refused this service's login: " + refused.getMessage(),
                "Check " + USERNAME_ENV + " and " + PASSWORD_ENV + " against the database's users.", failure);
    }

    /** The first SQL exception in the chain that says the login was refused, or null. */
    static SQLException loginRefusal(Throwable failure) {
        int depth = 0;
        for (Throwable t = failure; t != null && depth++ < 32; t = t.getCause()) {
            if (t instanceof SQLException sql && isLoginRefusal(sql)) {
                return sql;
            }
        }
        return null;
    }

    private static boolean isLoginRefusal(SQLException e) {
        String state = e.getSQLState();
        return "28P01".equals(state) || "28000".equals(state) || ("08004".equals(state) && noPasswordSent(e));
    }

    /** PgJDBC's wording when the server wants a password and none was configured. */
    private static boolean noPasswordSent(SQLException e) {
        String message = e.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("no password was provided");
    }

    private boolean passwordConfigured() {
        try {
            return StringUtils.hasText(environment.getProperty(PASSWORD_PROPERTY));
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            // e.g. spring.datasource.password=${SPRING_DATASOURCE_PASSWORD} with the variable unset
            return false;
        }
    }
}
