package com.itways.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.itways.common.util.UtcDateTimes;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every service runs on UTC and says so on the wire.
 *
 * <p>
 * {@code LocalDateTime.now()} is called in 40-odd places and follows the JVM's
 * default zone. The Docker images already run on UTC; a developer's laptop
 * didn't, so local data was stored in local time. Pinning the default here makes
 * both the same.
 *
 * <p>
 * Times then leave as {@code ...Z} (see {@link UtcDateTimes}). This applies to
 * Spring's own ObjectMapper — API responses and Feign calls — and deliberately
 * not to the private mappers that hash or store journey versions, whose output
 * must stay byte-for-byte stable.
 */
@Slf4j
@Configuration("timeConfig")
public class TimeConfig {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcDateTimes() {
        log.info("Times are UTC and serialized with an explicit Z");
        return builder -> builder
                .serializerByType(LocalDateTime.class, new UtcSerializer())
                .deserializerByType(LocalDateTime.class, new UtcDeserializer());
    }

    static final class UtcSerializer extends StdSerializer<LocalDateTime> {
        UtcSerializer() {
            super(LocalDateTime.class);
        }

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(UtcDateTimes.format(value));
        }
    }

    static final class UtcDeserializer extends StdDeserializer<LocalDateTime> {
        UtcDeserializer() {
            super(LocalDateTime.class);
        }

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.hasToken(JsonToken.VALUE_STRING)) {
                return UtcDateTimes.parse(p.getText());
            }
            if (p.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(p.getLongValue()), ZoneOffset.UTC);
            }
            // Arrays and anything else: whatever Jackson accepted before.
            return LocalDateTimeDeserializer.INSTANCE.deserialize(p, ctxt);
        }
    }
}
