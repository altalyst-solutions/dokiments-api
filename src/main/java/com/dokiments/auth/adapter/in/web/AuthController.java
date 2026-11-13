package com.dokiments.auth.adapter.in.web;

import com.dokiments.auth.application.port.in.SignIn;
import com.dokiments.auth.application.port.in.SignIn.SignInCommand;
import com.dokiments.auth.application.port.in.SignUp;
import com.dokiments.auth.application.port.in.SignUp.SignUpCommand;
import com.dokiments.auth.domain.AuthSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signing up, in and out, and reading who is signed in. Paths, bodies, status codes and messages are the web app's.
 *
 * <p>The session itself is the HttpOnly {@code dokiments-session} cookie: the response bodies only ever contain the
 * user, never a token. Each request that changes something needs the CSRF token from the {@code XSRF-TOKEN} cookie in
 * the {@code X-XSRF-TOKEN} header.
 */
@RestController
@Tag(name = "Auth")
class AuthController {

    private final SignUp signUp;
    private final SignIn signIn;
    private final SessionStore sessions;

    AuthController(SignUp signUp, SignIn signIn, SessionStore sessions) {
        this.signUp = signUp;
        this.signIn = signIn;
        this.sessions = sessions;
    }

    @PostMapping("/api/auth/sign-up")
    @Operation(
            summary = "Create an account and sign in",
            description = "Creates the Firebase account and its profile (role free), asks Firebase to send a"
                    + " verification email, and starts a session: the dokiments-session cookie, and the readable"
                    + " dokiments-session-expiry cookie with the session's end in milliseconds. The promo code is"
                    + " accepted but not redeemed yet.")
    @ApiResponse(responseCode = "200", description = "Signed up and signed in")
    @ApiResponse(
            responseCode = "401",
            description = "Firebase refused, with a message for the user, such as \"An account already exists for"
                    + " this email.\" or \"Use a stronger password with at least 6 characters.\"",
            content = @Content(schema = @Schema(ref = "#/components/schemas/Error")))
    AuthResponse signUp(
            @Valid @RequestBody SignUpRequest body, HttpServletRequest request, HttpServletResponse response) {
        AuthSession session = signUp.signUp(new SignUpCommand(body.displayName(), body.email(), body.password()));
        sessions.start(request, response, session);
        return new AuthResponse(AuthUserResponse.of(session));
    }

    @PostMapping("/api/auth/sign-in")
    @Operation(
            summary = "Sign in",
            description = "Checks the email address and password with Firebase and starts a session, as for sign-up."
                    + " The password has no maximum length. The promo code is accepted but not redeemed yet.")
    @ApiResponse(responseCode = "200", description = "Signed in")
    @ApiResponse(
            responseCode = "401",
            description = "Firebase refused, with a message for the user, such as \"The email or password is"
                    + " incorrect.\" or \"Too many attempts. Please try again later.\"",
            content = @Content(schema = @Schema(ref = "#/components/schemas/Error")))
    AuthResponse signIn(
            @Valid @RequestBody SignInRequest body, HttpServletRequest request, HttpServletResponse response) {
        AuthSession session = signIn.signIn(new SignInCommand(body.email(), body.password()));
        sessions.start(request, response, session);
        return new AuthResponse(AuthUserResponse.of(session));
    }

    @PostMapping("/api/auth/sign-out")
    @Operation(
            summary = "Sign out",
            description =
                    "Ends the session and expires both session cookies. Always succeeds, also when not signed" + " in.")
    SignOutResponse signOut(HttpServletRequest request, HttpServletResponse response) {
        sessions.end(request, response);
        return new SignOutResponse(true);
    }

    @GetMapping(value = "/api/auth/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Who is signed in",
            description = "The web app calls this when it starts. The ID token is refreshed first when it is about"
                    + " to run out.")
    @ApiResponse(
            responseCode = "200",
            description = "Signed in",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Not signed in, or the session has ended. The body is null, as the web app expects.",
            content = @Content(schema = @Schema(type = "null")))
    ResponseEntity<?> me(@AuthenticationPrincipal @Nullable AuthSession session) {
        if (session == null) {
            // The web app's answer for "no session" is a 401 whose body is the JSON value null.
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("null");
        }
        return ResponseEntity.ok(new AuthResponse(AuthUserResponse.of(session)));
    }

    /** Other fields are ignored, as in the web app. */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record SignUpRequest(
            @NotNull @Schema(example = "Jane Doe") String displayName,
            @NotNull @Schema(example = "jane@example.com") String email,
            @NotNull String password,
            @Nullable @Size(max = 64) String promoCode) {}

    /** Other fields are ignored, as in the web app. */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record SignInRequest(
            @NotNull @Schema(example = "jane@example.com") String email,
            @NotNull String password,
            @Nullable @Size(max = 64) String promoCode) {}

    record AuthResponse(AuthUserResponse user) {}

    /** Always {@code {"ok": true}}: signing out can't fail, whatever state the session was in. */
    @SuppressWarnings("SameParameterValue")
    record SignOutResponse(boolean ok) {}
}
