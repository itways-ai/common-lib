package com.itways.scope;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The assistant-scope beans, for services that store per-assistant rows.
 * Imported by {@link com.itways.annotation.EnableAssistantScope}; needs a
 * {@link JdbcTemplate} on the database that holds {@code assistant_directory}.
 * Also brings {@link LegacyAssistantHeaderConfig} (as {@code @EnableCommon}
 * does; registered once when both are on).
 */
@Configuration(proxyBeanMethods = false)
@Import(LegacyAssistantHeaderConfig.class)
public class AssistantScopeConfig {

    @Bean
    public AssistantDirectory assistantDirectory(JdbcTemplate jdbcTemplate) {
        return new AssistantDirectory(jdbcTemplate);
    }

    @Bean
    public ScopeRules scopeRules(AssistantDirectory assistantDirectory, JdbcTemplate jdbcTemplate) {
        return new ScopeRules(assistantDirectory, jdbcTemplate);
    }

    /** Lets controllers take a {@code @RequestedScope ListScope} parameter. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class RequestedScopeWebMvcConfig implements WebMvcConfigurer {

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new RequestedScopeArgumentResolver());
        }
    }
}
