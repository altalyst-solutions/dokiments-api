package com.dokiments.platform.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Names that users typed, made safe for subject lines and single lines of an email. */
class EmailLabelsTest {

    @Test
    void lineBreaksCantAddLinesOrHeaders() {
        assertThat(EmailLabels.clean("NDA\r\nBcc: someone@example.com", 80, "a document"))
                .isEqualTo("NDA Bcc: someone@example.com");
    }

    @Test
    void unicodeLineSeparatorsAndControlCharactersBecomeSpaces() {
        assertThat(EmailLabels.clean("One\u2028Two\u0007Three", 80, "a document"))
                .isEqualTo("One Two Three");
    }

    @Test
    void runsOfWhitespaceCollapse() {
        assertThat(EmailLabels.clean("  Service    agreement \t", 80, "a document"))
                .isEqualTo("Service agreement");
    }

    @Test
    void nothingLeftGivesTheFallback() {
        assertThat(EmailLabels.clean(" \n\t ", 80, "a document")).isEqualTo("a document");
    }

    @Test
    void longNamesAreCutWithAnEllipsisWithinTheLimit() {
        String cleaned = EmailLabels.clean("abcdefghij", 5, "x");

        assertThat(cleaned).isEqualTo("abcd…").hasSize(5);
    }

    @Test
    void cuttingNeverSplitsACharacter() {
        // Each emoji is two Java chars; cutting by chars could leave half of one.
        assertThat(EmailLabels.clean("😀😀😀😀😀😀", 4, "x")).isEqualTo("😀😀😀…");
    }

    @Test
    void aPersonIsNamedByTheirDisplayNameOrElseTheirAddress() {
        assertThat(EmailLabels.person("Jane Doe", "jane@example.com")).isEqualTo("Jane Doe");
        assertThat(EmailLabels.person("  ", "jane@example.com")).isEqualTo("jane@example.com");
    }

    @Test
    void timesAreShownInUtc() {
        assertThat(EmailLabels.utcTime(Instant.parse("2026-10-31T21:30:59Z"))).isEqualTo("31 Oct 2026, 21:30 UTC");
    }
}
