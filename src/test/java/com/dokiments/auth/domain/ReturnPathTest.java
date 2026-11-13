package com.dokiments.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Where people may be sent after signing in. */
class ReturnPathTest {

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard", "/my-documents?tab=shared", "/templates/classic#top"})
    void pathsOnThisSiteAreKept(String path) {
        assertThat(ReturnPath.of(path).path()).isEqualTo(path);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "dashboard", "https://evil.example/steal", "//evil.example/steal", "/\\evil.example"})
    void anythingElseBecomesTheDashboard(String asked) {
        assertThat(ReturnPath.of(asked)).isEqualTo(ReturnPath.DASHBOARD);
    }
}
