package com.dokiments.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.json.JsonWriter;

/** The severity each Logback level is given, in the names Google Cloud Logging understands. */
class CloudLoggingSeverityTest {

    private final JsonWriter<ILoggingEvent> writer =
            JsonWriter.of(members -> new CloudLoggingSeverity().customize(members));

    @ParameterizedTest
    @CsvSource({"ERROR, ERROR", "WARN, WARNING", "INFO, INFO", "DEBUG, DEBUG", "TRACE, DEBUG"})
    void eachLevelGetsCloudLoggingsName(String level, String severity) {
        var event = new LoggingEvent();
        event.setLevel(Level.toLevel(level));

        assertThat(writer.writeToString(event)).isEqualTo("{\"severity\":\"" + severity + "\"}");
    }
}
