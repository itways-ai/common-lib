package com.itways.common.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The request-id rule the gateway and the servlet services share (ARC-25). */
class RequestIdsTest {

    @Test
    void theNamesArePinned() {
        assertThat(RequestIds.HEADER).isEqualTo("X-Request-Id");
        assertThat(RequestIds.MDC_KEY).isEqualTo("requestId");
        assertThat(RequestIds.AMQP_HEADER).isEqualTo("x-request-id");
        assertThat(RequestIds.REQUEST_ATTRIBUTE).isEqualTo("com.itways.requestId");
    }

    @Test
    void wellFormedIsOneTo64SafeCharacters() {
        assertThat(RequestIds.isWellFormed("a")).isTrue();
        assertThat(RequestIds.isWellFormed("3f2a9c1e-7b4d-4e0a-9d7c-1a2b3c4d5e6f")).isTrue();
        assertThat(RequestIds.isWellFormed("req_1.2-B")).isTrue();
        assertThat(RequestIds.isWellFormed("x".repeat(64))).isTrue();

        assertThat(RequestIds.isWellFormed(null)).isFalse();
        assertThat(RequestIds.isWellFormed("")).isFalse();
        assertThat(RequestIds.isWellFormed(" ")).isFalse();
        assertThat(RequestIds.isWellFormed("x".repeat(65))).isFalse();
        assertThat(RequestIds.isWellFormed("has space")).isFalse();
        assertThat(RequestIds.isWellFormed("line\nbreak")).isFalse();
        assertThat(RequestIds.isWellFormed("abc\n")).isFalse();
        assertThat(RequestIds.isWellFormed("quote\"")).isFalse();
        assertThat(RequestIds.isWellFormed("semi;colon")).isFalse();
        assertThat(RequestIds.isWellFormed("ünïcode")).isFalse();
    }

    @Test
    void generateGivesARandomUuid() {
        String first = RequestIds.generate();
        String second = RequestIds.generate();

        assertThat(UUID.fromString(first).toString()).isEqualTo(first);
        assertThat(first).isNotEqualTo(second);
        assertThat(RequestIds.isWellFormed(first)).isTrue();
    }

    @Test
    void acceptKeepsAWellFormedIdTrimmed() {
        assertThat(RequestIds.accept("abc-123")).isEqualTo("abc-123");
        assertThat(RequestIds.accept("  abc-123\t")).isEqualTo("abc-123");
    }

    @Test
    void acceptReplacesAnythingElseWithANewId() {
        for (String bad : new String[] { null, "", "   ", "x".repeat(65), "two words", "a\r\nforged: log line" }) {
            String accepted = RequestIds.accept(bad);
            assertThat(accepted).as("accept(%s)", bad).isNotEqualTo(bad);
            assertThat(UUID.fromString(accepted).toString()).isEqualTo(accepted);
        }
    }
}
