package com.itways.contracts.journey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Ask for the journeys nearest a point in intent space
 * ({@code POST /api/journeys/intent-catalog/shortlist}).
 *
 * <p>
 * {@code limit} is how many survive to reach the model. It is the caller's
 * choice because the caller knows what it can afford to put in a prompt; null
 * means journey-service's default.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntentShortlistRequest(
        float[] queryVector,
        Integer limit) {
}
