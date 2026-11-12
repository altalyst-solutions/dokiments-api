package com.dokiments.platform.apidocs;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.support.IntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps {@code docs/openapi.yaml}, the committed description of the API, in step with the code.
 *
 * <p>The web app depends on the exact contract, so a change to it must be deliberate and visible in review. This test
 * fails when the description generated from the controllers differs from the committed file. After an intended change,
 * {@code ./gradlew updateOpenApi} rewrites the file, and its diff shows the new contract.
 */
@IntegrationTest
@AutoConfigureMockMvc
// As in the dev profile.
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
class OpenApiSpecIT {

    static final Path COMMITTED = Path.of("docs/openapi.yaml");

    @Autowired
    private MockMvcTester mvc;

    @Test
    void everySchemaTheDescriptionRefersToIsInIt() {
        JsonNode description = JsonMapper.builder()
                .build()
                .readTree(mvc.get().uri("/v3/api-docs").exchange().getResponse().getContentAsByteArray());
        Set<String> defined =
                new HashSet<>(description.path("components").path("schemas").propertyNames());
        List<String> referenced = description.findValues("$ref").stream()
                .map(JsonNode::asString)
                .map(ref -> ref.substring(ref.lastIndexOf('/') + 1))
                .distinct()
                .toList();

        assertThat(referenced)
                .isNotEmpty()
                .allSatisfy(name -> assertThat(defined).contains(name));
    }

    @Test
    void swaggerUiIsServedWhereTheDescriptionIsOn() {
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk();
    }

    @Test
    void theCommittedDescriptionMatchesTheCode() throws IOException {
        String generated =
                mvc.get().uri("/v3/api-docs.yaml").exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(generated).as("generated API description").startsWith("openapi:");

        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(COMMITTED, generated, StandardCharsets.UTF_8);
            return;
        }

        assertThat(Files.exists(COMMITTED))
                .as("%s doesn't exist; create it with ./gradlew updateOpenApi", COMMITTED)
                .isTrue();
        assertThat(generated)
                .as(
                        "The API no longer matches %s. If the change is intended, run ./gradlew updateOpenApi and commit"
                                + " the file; otherwise, the code changed the API contract by mistake.",
                        COMMITTED)
                .isEqualTo(Files.readString(COMMITTED, StandardCharsets.UTF_8));
    }
}
