package com.dokiments.platform.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** The safety rules that decide which Firestore the application may use. */
class FirestorePropertiesTest {

    @Test
    void theEmulatorWithADemoProjectIsAllowed() {
        var properties = new FirestoreProperties("demo-dokiments", "localhost:8085", true);

        assertThat(properties.usesEmulator()).isTrue();
    }

    @Test
    void aRealProjectWithoutTheEmulatorIsAllowedWhereTheEmulatorIsntRequired() {
        var properties = new FirestoreProperties("dokiments-prod", null, false);

        assertThat(properties.usesEmulator()).isFalse();
    }

    @Test
    void anEmptyEmulatorHostMeansNoEmulator() {
        // Environment variables that aren't set usually arrive as empty strings.
        assertThat(new FirestoreProperties("dokiments-prod", "  ", false).usesEmulator())
                .isFalse();
    }

    @Test
    void theEmulatorWithARealProjectIdIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FirestoreProperties("dokiments-prod", "localhost:8085", false))
                .withMessageContaining("must use a project ID starting with \"demo-\"");
    }

    @Test
    void aDemoProjectWithoutTheEmulatorIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FirestoreProperties("demo-dokiments", null, false))
                .withMessageContaining("only exists in the emulator");
    }

    @Test
    void runningWithoutTheEmulatorWhereItIsRequiredIsRefused() {
        // The case that matters most: a laptop with a real project ID and Google credentials.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FirestoreProperties("dokiments-prod", null, true))
                .withMessageContaining("only allows the Firestore emulator");
    }

    @Test
    void aMissingProjectIdIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FirestoreProperties(" ", "localhost:8085", true))
                .withMessageContaining("project-id must be set");
    }
}
