package com.itways.contracts.knowledge;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One batch of an account's passages whose vector another embedding model
 * made, oldest first, with the exact text to embed again.
 *
 * <p>
 * Served by journey-service ({@code /api/journeys/internal/knowledge/embeddings/stale}),
 * called by speech-service, which owns the model. One definition, so the two
 * cannot drift.
 *
 * @param remaining how many passages of the account are stale, this batch included
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StalePassages(List<Passage> passages, long remaining) {

	/** A passage to embed again: its id and the text its vector is made from. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Passage(long id, String text) {
	}
}
