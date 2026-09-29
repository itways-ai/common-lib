package com.itways.contracts.journey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One journey's routing text: what it is called and how people ask for it.
 *
 * <p>
 * Served by journey-service ({@code GET /api/journeys/routing-summary}): the
 * console compares it to warn about confusable journeys, and conversation-service
 * turns it into the voice channel's speech hints.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JourneyRoutingSummary(
        Long journeyId,
        String name,
        String triggerIntent,
        List<String> exampleUtterances) {
}
