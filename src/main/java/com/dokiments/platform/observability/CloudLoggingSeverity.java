package com.dokiments.platform.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Adds a top-level {@code severity} to each JSON log line, as Google Cloud Logging reads it.
 *
 * <p>The lines are in Elastic Common Schema, which puts the level under {@code log.level}. Cloud Logging only looks for
 * {@code severity}, and without it shows every line as "Default", so errors couldn't be told apart from the rest. Its
 * names differ from Logback's for warnings ({@code WARNING}) and it has no trace level ({@code DEBUG} instead). Other
 * log platforms ignore the extra field, so the format stays the same everywhere else.
 *
 * <p>Registered with {@code logging.structured.json.customizer} in {@code application.yml}; Spring Boot creates it.
 */
class CloudLoggingSeverity implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.add("severity", ILoggingEvent::getLevel).as(CloudLoggingSeverity::severity);
    }

    static String severity(Level level) {
        return switch (level.toInt()) {
            case Level.ERROR_INT -> "ERROR";
            case Level.WARN_INT -> "WARNING";
            case Level.INFO_INT -> "INFO";
            default -> "DEBUG";
        };
    }
}
