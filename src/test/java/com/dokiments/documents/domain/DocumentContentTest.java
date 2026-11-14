package com.dokiments.documents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class DocumentContentTest {

    @Test
    void aNameIsOneToTwoHundredCharactersAndKeptAsTyped() {
        assertThat(DocumentContent.name(" Invoice ")).isEqualTo(" Invoice ");
        assertThat(DocumentContent.name("n".repeat(200))).hasSize(200);

        assertInvalid(() -> DocumentContent.name(""));
        assertInvalid(() -> DocumentContent.name("n".repeat(201)));
    }

    @Test
    void valuesAreAtMostFiftyTextsOfAtMostTwentyThousandCharacters() {
        var fifty = IntStream.range(0, 50).boxed().collect(Collectors.toMap(i -> "k" + i, i -> "v"));
        assertThat(DocumentContent.values(fifty)).hasSize(50);
        assertThat(DocumentContent.values(Map.of("notes", "x".repeat(20_000)))).hasSize(1);
        assertThat(DocumentContent.values(Map.of())).isEmpty();

        var fiftyOne = new HashMap<>(fifty);
        fiftyOne.put("k50", "v");
        assertInvalid(() -> DocumentContent.values(fiftyOne));
        assertInvalid(() -> DocumentContent.values(Map.of("notes", "x".repeat(20_001))));
        var missing = new HashMap<String, String>();
        missing.put("notes", null);
        assertInvalid(() -> DocumentContent.values(missing));
    }

    @Test
    void aChangeNeedsANameOrValues() {
        assertThat(DocumentChanges.of("New name", null)).isEqualTo(new DocumentChanges("New name", null));
        assertThat(DocumentChanges.of(null, Map.of()).values()).isEmpty();

        // Failing is the point here, which IntelliJ's check of the method's contract points out.
        //noinspection DataFlowIssue
        assertInvalid(() -> DocumentChanges.of(null, null));
        assertInvalid(() -> DocumentChanges.of("", null));
    }

    @Test
    void aTemplateFilterMayNotBeEmpty() {
        assertThat(DocumentFilter.of(null).templateId()).isNull();
        assertThat(DocumentFilter.of("classic-invoice").templateId()).isEqualTo("classic-invoice");
        assertThatThrownBy(() -> DocumentFilter.of(""))
                .isInstanceOf(com.dokiments.shared.error.InvalidRequestException.class)
                .hasMessage("Invalid query parameters.");
    }

    @Test
    void aNewIdHasTheWebAppsForm() {
        var id = DocumentId.generate(java.time.Instant.ofEpochMilli(1_763_000_000_000L), new java.util.Random(1));

        assertThat(id.value()).matches("doc_" + Long.toString(1_763_000_000_000L, 36) + "_[0-9a-z]{6}");
        assertThat(DocumentId.parse("a/b")).isEmpty();
        assertThat(DocumentId.parse("doc_x")).contains(new DocumentId("doc_x"));
    }

    private static void assertInvalid(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(InvalidDocumentException.class).hasMessage("Invalid request body.");
    }
}
