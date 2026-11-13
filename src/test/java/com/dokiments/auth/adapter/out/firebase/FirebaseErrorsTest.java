package com.dokiments.auth.adapter.out.firebase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The messages the web app shows for Firebase's error codes. */
class FirebaseErrorsTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "INVALID_LOGIN_CREDENTIALS | The email or password is incorrect.",
                "INVALID_PASSWORD | The email or password is incorrect.",
                "EMAIL_EXISTS | An account already exists for this email.",
                "WEAK_PASSWORD : Password should be at least 6 characters | Use a stronger password with at least 6 characters.",
                "TOO_MANY_ATTEMPTS_TRY_LATER : Access disabled | Too many attempts. Please try again later.",
                "USER_DISABLED | This account has been disabled.",
                "SOMETHING_NEW | Something went wrong. Please try again."
            })
    void eachCodeHasTheWebAppsMessage(String code, String message) {
        assertThat(FirebaseErrors.messageFor(code)).isEqualTo(message);
    }
}
