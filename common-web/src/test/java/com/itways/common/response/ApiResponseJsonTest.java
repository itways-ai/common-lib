package com.itways.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * The envelope's JSON (ARC-25): exactly the five former fields, in their order,
 * while no reference is set; the sixth, {@code reference}, only when it is.
 * Lives here, not in common-core, because common-core's tests have no Jackson
 * databind.
 */
class ApiResponseJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static List<String> fields(ApiResponse<?> response) throws Exception {
        JsonNode node = MAPPER.readTree(MAPPER.writeValueAsString(response));
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void withoutAReferenceTheFiveFieldsAreUnchanged() throws Exception {
        assertThat(fields(ApiResponse.error("Nope", "X"))).containsExactly("timestamp", "status", "message", "data",
                "errorCode");
        assertThat(fields(ApiResponse.success("x"))).containsExactly("timestamp", "status", "message", "data",
                "errorCode");
        assertThat(fields(ApiResponse.success("done", 1))).hasSize(5);
    }

    @Test
    void aReferenceIsTheSixthField() throws Exception {
        ApiResponse<Void> response = ApiResponse.error("Nope", "X");
        response.setReference("req-1");

        assertThat(fields(response)).containsExactly("timestamp", "status", "message", "data", "errorCode",
                "reference");
        assertThat(MAPPER.readTree(MAPPER.writeValueAsString(response)).get("reference").asText()).isEqualTo("req-1");
    }

    @Test
    void theFormerConstructorAndFactoriesStillWork() throws Exception {
        LocalDateTime at = LocalDateTime.of(2026, 9, 29, 10, 0);
        ApiResponse<String> five = new ApiResponse<>(at, "error", "m", "d", "E");
        ApiResponse<String> six = new ApiResponse<>(at, "error", "m", "d", "E", "r");

        assertThat(five.getReference()).isNull();
        assertThat(six.getReference()).isEqualTo("r");
        assertThat(five).isNotEqualTo(six);
        assertThat(ApiResponse.error("m", "E").getStatus()).isEqualTo("error");
        assertThat(ApiResponse.error("m", "E").getReference()).isNull();
        assertThat(ApiResponse.success("d").getReference()).isNull();

        // Reads back, with and without the field (a client on either version).
        assertThat(MAPPER.readValue("{\"status\":\"error\",\"reference\":\"r\"}", ApiResponse.class).getReference())
                .isEqualTo("r");
        assertThat(MAPPER.readValue("{\"status\":\"error\"}", ApiResponse.class).getReference()).isNull();
    }
}
