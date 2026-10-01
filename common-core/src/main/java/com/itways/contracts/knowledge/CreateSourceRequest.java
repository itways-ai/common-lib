package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Adds a source to an index, or re-queues the index's source of the same name (2.2.0;
 * journey-service {@code POST /{index}/sources}). The passages are stored at once without
 * vectors; the ingestion worker embeds them. A website source comes without passages: the
 * worker fetches it.
 *
 * @param kind     a {@code KnowledgeSourceView.KIND_*} value
 * @param name     the file name, the URL, or "Typed rows"; unique within the index
 * @param url      the page, for a website source
 * @param passages what was read; null or empty for a website source
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateSourceRequest(
        String kind,
        String name,
        String url,
        List<SourcePassage> passages) {

    /** A website page, fetched and read by the worker; named by its URL. */
    public static CreateSourceRequest website(String url) {
        return new CreateSourceRequest(KnowledgeSourceView.KIND_WEBSITE, url, url, null);
    }
}
