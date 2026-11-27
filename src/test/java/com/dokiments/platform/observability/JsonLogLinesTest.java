package com.dokiments.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

/**
 * The log lines as a deployed container writes them: the application's own {@code application.yml} and no profile, so
 * the settings under {@code logging.structured} are the ones that apply in production. Only the logging setup is
 * started, not the application's beans, so it needs neither Redis nor Firestore.
 */
@ExtendWith(OutputCaptureExtension.class)
class JsonLogLinesTest {

    @Test
    void aLineCarriesTheSeverityCloudLoggingReadsNextToTheEcsLevel(CapturedOutput output) {
        try (var _ = new SpringApplicationBuilder(NoBeans.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off")) {
            LoggerFactory.getLogger(JsonLogLinesTest.class).warn("a line to look at");
        }

        assertThat(output.getOut().lines().filter(line -> line.contains("a line to look at")))
                .singleElement()
                .satisfies(line -> assertThat(line)
                        .startsWith("{")
                        .contains("\"severity\":\"WARNING\"")
                        .contains("\"log\":{\"level\":\"WARN\""));
    }

    @Configuration(proxyBeanMethods = false)
    static class NoBeans {}
}
