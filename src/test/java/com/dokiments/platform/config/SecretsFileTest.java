package com.dokiments.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Secrets read from a mounted properties file, as on Cloud Run: {@code SPRING_CONFIG_IMPORT=file:/path} names the file,
 * whose keys are the environment variables' names ({@code REDIS_URL=...}). {@code application.yml} maps each variable
 * to its setting, so a value from the file arrives where the variable's would.
 *
 * <p>Only the configuration is loaded, with the application's own {@code application.yml} and no profile, not its
 * beans, so it needs neither Redis nor Firestore.
 */
class SecretsFileTest {

    @TempDir
    private Path folder;

    @Test
    void theFilesValuesReachTheSettingsTheirVariablesMapTo() throws Exception {
        Path file = folder.resolve("dokiments-api.properties");
        Files.writeString(file, """
                REDIS_URL=rediss://default:from-the-file@redis.example:6379
                FIREBASE_API_KEY=key-from-the-file
                GOOGLE_CLIENT_SECRET=secret-from-the-file
                RESEND_API_KEY=re_from_the_file
                """);

        try (var context = start("file:" + file)) {
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.data.redis.url"))
                    .isEqualTo("rediss://default:from-the-file@redis.example:6379");
            assertThat(environment.getProperty("dokiments.auth.api-key")).isEqualTo("key-from-the-file");
            assertThat(environment.getProperty("dokiments.auth.google.client-secret"))
                    .isEqualTo("secret-from-the-file");
            assertThat(environment.getProperty("dokiments.email.resend-api-key"))
                    .isEqualTo("re_from_the_file");
        }
    }

    @Test
    void aMissingFileStopsTheApplicationFromStarting() {
        Path missing = folder.resolve("not-mounted.properties");

        assertThatThrownBy(() -> start("file:" + missing).close())
                .isInstanceOf(ConfigDataResourceNotFoundException.class);
    }

    /** Loads the configuration with the file imported, as SPRING_CONFIG_IMPORT does. */
    private static ConfigurableApplicationContext start(String configImport) {
        return new SpringApplicationBuilder(NoBeans.class)
                .web(WebApplicationType.NONE)
                .run("--spring.config.import=" + configImport, "--spring.main.banner-mode=off");
    }

    @Configuration(proxyBeanMethods = false)
    static class NoBeans {}
}
