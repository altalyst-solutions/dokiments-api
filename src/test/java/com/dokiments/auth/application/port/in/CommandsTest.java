package com.dokiments.auth.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.auth.application.port.in.SignIn.SignInCommand;
import com.dokiments.auth.application.port.in.SignUp.SignUpCommand;
import com.dokiments.platform.web.ErrorMessages;
import com.dokiments.shared.error.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The web app's checks on sign-in and sign-up bodies. */
class CommandsTest {

    @Test
    void addressesAreTrimmedButKeepTheirCase() {
        assertThat(new SignInCommand(" Jane@Example.com ", "x").email()).isEqualTo("Jane@Example.com");
    }

    @Test
    void aSignInPasswordHasNoMaximum() {
        assertThat(new SignInCommand("jane@example.com", "p".repeat(500)).password())
                .hasSize(500);
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "NULL",
            value = {"jane@example, secret", "jane@example.com, ''"})
    void invalidSignInsAreAnInvalidBody(String email, String password) {
        assertThatThrownBy(() -> new SignInCommand(email, password))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(ErrorMessages.INVALID_BODY);
    }

    @Test
    void aSignUpNameIsTrimmed() {
        assertThat(new SignUpCommand("  Jane Doe ", "jane@example.com", "secret").displayName())
                .isEqualTo("Jane Doe");
    }

    @Test
    void aSignUpNeedsANameAndAPasswordWithinLimits() {
        assertThatThrownBy(() -> new SignUpCommand("   ", "jane@example.com", "secret"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new SignUpCommand("n".repeat(201), "jane@example.com", "secret"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new SignUpCommand("Jane", "jane@example.com", "p".repeat(129)))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(new SignUpCommand("n".repeat(200), "jane@example.com", "p".repeat(128)))
                .isNotNull();
    }

    @Test
    void aShortSignUpPasswordIsLeftToFirebase() {
        // Firebase answers with a message that says what is needed, which is more useful than "Invalid request body."
        assertThat(new SignUpCommand("Jane", "jane@example.com", "a").password())
                .isEqualTo("a");
    }

    @Test
    void passwordsAreNeverPrinted() {
        assertThat(new SignInCommand("jane@example.com", "hunter2").toString()).doesNotContain("hunter2");
        assertThat(new SignUpCommand("Jane", "jane@example.com", "hunter2").toString())
                .doesNotContain("hunter2");
    }
}
