package com.itways.common.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.io.support.SpringFactoriesLoader.FailureHandler;
import org.springframework.mock.env.MockEnvironment;

/** A start refused by the database names the variable to set (2.1.0). */
class DatabaseLoginFailureAnalyzerTest {

    private static final SQLException NO_PASSWORD = new SQLException(
            "The server requested SCRAM-based authentication, but no password was provided.", "08004");
    private static final SQLException WRONG_PASSWORD = new SQLException(
            "FATAL: password authentication failed for user \"postgres\"", "28P01");

    @Test
    void noPasswordConfiguredNamesTheVariable() {
        FailureAnalysis analysis = analyzer(new MockEnvironment()).analyze(startupFailure(NO_PASSWORD));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("spring.datasource.password is not set")
                .contains("no password was provided");
        assertThat(analysis.getAction()).startsWith("Set SPRING_DATASOURCE_PASSWORD.");
    }

    @Test
    void aRefusedLoginWithoutAConfiguredPasswordAlsoNamesIt() {
        // e.g. spring.datasource.password=${SPRING_DATASOURCE_PASSWORD} left unresolved and sent as is
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.password",
                "${SPRING_DATASOURCE_PASSWORD}");

        FailureAnalysis analysis = analyzer(environment).analyze(startupFailure(WRONG_PASSWORD));

        assertThat(analysis.getAction()).startsWith("Set SPRING_DATASOURCE_PASSWORD.");
    }

    @Test
    void aWrongPasswordPointsAtBothSettings() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.password", "not-it");

        FailureAnalysis analysis = analyzer(environment).analyze(startupFailure(WRONG_PASSWORD));

        assertThat(analysis.getDescription()).startsWith("The database refused this service's login")
                .contains("password authentication failed");
        assertThat(analysis.getAction()).contains("SPRING_DATASOURCE_USERNAME").contains("SPRING_DATASOURCE_PASSWORD");
    }

    @Test
    void anUnknownRoleIsARefusedLoginToo() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.password", "secret");

        FailureAnalysis analysis = analyzer(environment).analyze(
                startupFailure(new SQLException("FATAL: role \"nobody\" does not exist", "28000")));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getAction()).contains("SPRING_DATASOURCE_USERNAME");
    }

    @Test
    void otherFailuresAreLeftToOtherAnalyzers() {
        DatabaseLoginFailureAnalyzer analyzer = analyzer(new MockEnvironment());

        assertThat(analyzer.analyze(startupFailure(new SQLException("relation \"x\" does not exist", "42P01"))))
                .isNull();
        assertThat(analyzer.analyze(startupFailure(new SQLException("Connection refused", "08001")))).isNull();
        assertThat(analyzer.analyze(startupFailure(new SQLException("The connection attempt failed.", "08004"))))
                .isNull();
        assertThat(analyzer.analyze(new IllegalStateException("no database involved"))).isNull();
    }

    @Test
    void registeredForSpringBootAndGivenTheEnvironment() {
        // What Spring Boot's FailureAnalyzers does on a failed start; analyzers whose
        // libraries are not on this classpath are skipped, as Boot skips them.
        ArgumentResolver arguments = ArgumentResolver.of(BeanFactory.class, new DefaultListableBeanFactory())
                .and(Environment.class, new MockEnvironment());

        List<FailureAnalyzer> analyzers = SpringFactoriesLoader
                .forDefaultResourceLocation(getClass().getClassLoader())
                .load(FailureAnalyzer.class, arguments, FailureHandler.handleMessage((message, failure) -> {
                }));

        assertThat(analyzers).hasAtLeastOneElementOfType(DatabaseLoginFailureAnalyzer.class);
    }

    private static DatabaseLoginFailureAnalyzer analyzer(MockEnvironment environment) {
        return new DatabaseLoginFailureAnalyzer(environment);
    }

    /** How a start fails: the context wraps the pool's failure, which wraps the driver's. */
    private static RuntimeException startupFailure(SQLException driver) {
        return new IllegalStateException("Error creating bean with name 'flywayInitializer'",
                new RuntimeException("Failed to initialize pool: " + driver.getMessage(), driver));
    }
}
