package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Changes an index's settings (2.2.0, the embedding switch): journey-service's
 * {@code PATCH /{index}/settings} and conversation-service's public facade.
 *
 * <p>
 * {@code ignoreTerms} are words and phrases (brand names, say) left out of the passage and
 * query text before either is embedded, case-insensitively and as whole words or phrases. At
 * most {@link #MAX_TERMS} terms, each {@link #MIN_TERM_LENGTH}..{@link #MAX_TERM_LENGTH}
 * characters after trimming; journey-service trims them and drops case-insensitive duplicates,
 * and answers 400 {@code IGNORE_TERMS_INVALID} otherwise. A change re-embeds the whole index.
 *
 * @param ignoreTerms the complete new list (replaces the old one); null leaves it unchanged,
 *                    empty clears it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IndexSettingsRequest(List<String> ignoreTerms) {

    /** The most terms an index may have. */
    public static final int MAX_TERMS = 20;
    /** The shortest term, after trimming. */
    public static final int MIN_TERM_LENGTH = 1;
    /** The longest term, after trimming. */
    public static final int MAX_TERM_LENGTH = 60;
}
