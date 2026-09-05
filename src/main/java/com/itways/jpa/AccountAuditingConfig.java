package com.itways.jpa;

import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JPA auditing with the calling account as the auditor.
 *
 * <p>
 * The security filters in this library put the caller's details on the
 * {@link Authentication} as a map; {@code accountId} in that map is what ends
 * up in {@code created_by} / {@code updated_by}. Every service that audits
 * rows by account needs exactly this bean, so it lives here — opt in with
 * {@code @EnableAccountAuditing} in a service that has Spring Data JPA.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AccountAuditingConfig {

	@Bean
	public AuditorAware<String> auditorProvider() {
		return () -> {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication == null || !authentication.isAuthenticated()) {
				return Optional.empty();
			}
			Object details = authentication.getDetails();
			if (details instanceof Map<?, ?> map) {
				return Optional.ofNullable((String) map.get("accountId"));
			}
			return Optional.empty();
		};
	}
}
