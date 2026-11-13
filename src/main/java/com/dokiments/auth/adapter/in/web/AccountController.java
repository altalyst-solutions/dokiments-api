package com.dokiments.auth.adapter.in.web;

import com.dokiments.auth.adapter.in.web.AuthController.AuthResponse;
import com.dokiments.auth.application.port.in.ManageAccount;
import com.dokiments.auth.application.port.in.ManageAccount.ResetPasswordCommand;
import com.dokiments.auth.application.port.in.ManageAccount.UpdateProfileCommand;
import com.dokiments.auth.application.port.in.RefreshSession;
import com.dokiments.auth.domain.AuthSession;
import com.dokiments.shared.error.UnauthenticatedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Changes to one's own account, and the password reset. Paths, bodies, status codes and messages are the web app's.
 *
 * <p>Every route except the password reset needs a session ({@code 401 Unauthorised.} otherwise). A change that gets
 * new Firebase tokens stores them in the session, which still ends when it would have.
 */
@RestController
@Tag(name = "Account")
class AccountController {

    private static final String ERROR = "#/components/schemas/Error";

    private final ManageAccount account;
    private final RefreshSession refreshSession;
    private final SessionStore sessions;

    AccountController(ManageAccount account, RefreshSession refreshSession, SessionStore sessions) {
        this.account = account;
        this.refreshSession = refreshSession;
        this.sessions = sessions;
    }

    @PostMapping("/api/auth/update-password")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @Operation(
            summary = "Change the password",
            description = "The new password is at most 128 characters; Firebase judges whether it is strong enough."
                    + " When the session is too old for a password change, the answer is 403 with the code"
                    + " REAUTH_REQUIRED: confirm the current password with /api/auth/reauthenticate, then try again.")
    @ApiResponse(responseCode = "200", description = "Changed")
    @ApiResponse(
            responseCode = "400",
            description = "A Google account (\"This account signs in with Google — there is no password to"
                    + " change.\"), an invalid body, or Firebase refusing, with its message",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "403",
            description = "REAUTH_REQUIRED, or a missing CSRF token",
            content = @Content(schema = @Schema(ref = ERROR)))
    OkResponse updatePassword(
            @AuthenticationPrincipal AuthSession session,
            @RequestBody PasswordRequest body,
            HttpServletRequest request) {
        sessions.update(request, account.changePassword(session, body.passwordOrEmpty()));
        return new OkResponse(true);
    }

    @PostMapping("/api/auth/reauthenticate")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @Operation(
            summary = "Confirm the current password",
            description = "Signs in again with the session's own address and the password sent, so a change that"
                    + " needs a recent sign-in can go ahead. The session keeps its end.")
    @ApiResponse(responseCode = "200", description = "Confirmed")
    @ApiResponse(
            responseCode = "400",
            description = "A Google account, an invalid body, or a wrong password (\"The email or password is"
                    + " incorrect.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    OkResponse reauthenticate(
            @AuthenticationPrincipal AuthSession session,
            @RequestBody PasswordRequest body,
            HttpServletRequest request) {
        sessions.update(request, account.reauthenticate(session, body.passwordOrEmpty()));
        return new OkResponse(true);
    }

    @PostMapping("/api/auth/update-profile")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @Operation(
            summary = "Change the name and details in the settings",
            description = "Every field is trimmed; a missing one counts as empty, which clears it. The answer has the"
                    + " user as changed, with empty details as empty text.")
    @ApiResponse(responseCode = "200", description = "Changed")
    @ApiResponse(
            responseCode = "400",
            description = "An invalid body, or Firebase refusing, with its message",
            content = @Content(schema = @Schema(ref = ERROR)))
    AuthResponse updateProfile(
            @AuthenticationPrincipal AuthSession session,
            @Valid @RequestBody ProfileRequest body,
            HttpServletRequest request) {
        var updated = account.updateProfile(
                session,
                UpdateProfileCommand.of(
                        body.displayName(),
                        orEmpty(body.address()),
                        orEmpty(body.brn()),
                        orEmpty(body.businessEmail()),
                        orEmpty(body.companyName()),
                        orEmpty(body.fullName()),
                        orEmpty(body.phone()),
                        orEmpty(body.tel()),
                        orEmpty(body.website())));
        sessions.update(request, updated);
        return new AuthResponse(AuthUserResponse.of(updated));
    }

    @PostMapping("/api/auth/send-verification")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @Operation(
            summary = "Send the verification email again",
            description = "Nothing is sent when the address is verified already. The answer has no body either way.")
    @ApiResponse(responseCode = "204", description = "Sent, or not needed")
    @ApiResponse(
            responseCode = "429",
            description = "Firebase is throttling the account: \"Too many attempts. Please try again later.\"",
            content = @Content(schema = @Schema(ref = ERROR)))
    ResponseEntity<Void> sendVerification(@AuthenticationPrincipal AuthSession session) {
        account.resendVerificationEmail(session);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/refresh-verification")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @Operation(
            summary = "Pick up a verified address",
            description = "Refreshes the session's tokens now, so the user's emailVerified reflects a verification"
                    + " link opened in the meantime. A refresh token Firebase refuses ends the session (401).")
    @ApiResponse(responseCode = "200", description = "The user, as of the new tokens")
    AuthResponse refreshVerification(
            @AuthenticationPrincipal AuthSession session, HttpServletRequest request, HttpServletResponse response) {
        AuthSession refreshed;
        try {
            refreshed = refreshSession.refresh(session);
        } catch (UnauthenticatedException refused) {
            // Firebase refused the refresh token: the session can't continue, so it ends here, cookies and all.
            sessions.end(request, response);
            // The web app's generic answer, not Firebase's message.
            throw new UnauthenticatedException(UnauthenticatedException.DEFAULT_MESSAGE, refused);
        }
        sessions.update(request, refreshed);
        return new AuthResponse(AuthUserResponse.of(refreshed));
    }

    @PostMapping("/api/auth/forgot-password")
    @Operation(
            summary = "Send a password reset link",
            description = "Public. Firebase sends the link to the account with this address.")
    @ApiResponse(responseCode = "200", description = "Sent")
    @ApiResponse(
            responseCode = "400",
            description = "An invalid body, or Firebase refusing, with its message",
            content = @Content(schema = @Schema(ref = ERROR)))
    OkResponse forgotPassword(@Valid @RequestBody EmailRequest body) {
        account.sendPasswordResetEmail(new ResetPasswordCommand(body.email()));
        return new OkResponse(true);
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    /** The password, checked by the use case after it has made sure the account has one. */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record PasswordRequest(@Nullable String password) {
        String passwordOrEmpty() {
            return password == null ? "" : password;
        }

        @Override
        public String toString() {
            return "PasswordRequest[]";
        }
    }

    /** The settings. A missing field counts as empty, as in the web app. Other fields are ignored. */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record ProfileRequest(
            @NotNull @Schema(example = "Jane Doe") String displayName,
            @Nullable String address,
            @Nullable String brn,
            @Nullable String businessEmail,
            @Nullable String companyName,
            @Nullable String fullName,
            @Nullable String phone,
            @Nullable String tel,
            @Nullable String website) {}

    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record EmailRequest(
            @NotNull @Schema(example = "jane@example.com") String email) {}

    /** Always {@code {"ok": true}} when the change went through. */
    @SuppressWarnings("SameParameterValue")
    record OkResponse(boolean ok) {}
}
