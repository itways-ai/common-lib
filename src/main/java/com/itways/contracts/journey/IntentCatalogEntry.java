package com.itways.contracts.journey;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One journey as the intent classifier sees it: the code it routes on and the
 * authored text that tells it apart from the others.
 *
 * <p>
 * Served by journey-service ({@code GET /api/journeys/intent-catalog}) and read
 * by assistant-service. One definition, so a field added on one side cannot be
 * silently missing on the other; unknown fields are ignored so the two services
 * can still be deployed one at a time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntentCatalogEntry(
		Long id,
		String intent,
		String name,
		String description,
		List<String> exampleUtterances) {
}
