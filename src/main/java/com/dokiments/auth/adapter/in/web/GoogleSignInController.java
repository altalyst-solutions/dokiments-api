package com.dokiments.auth.adapter.in.web;

import com.dokiments.auth.application.port.in.SignInWithGoogle;
import com.dokiments.auth.application.port.out.PendingSignIns;
import com.dokiments.auth.domain.ReturnPath;
import com.dokiments.promo.PromoAtSignIn;
import com.dokiments.promo.PromoOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google sign-in, as the web app's: browser redirects, not JSON answers.
 *
 * <p>The start remembers a random {@code state} in a short-lived cookie and sends the browser to Google; Google sends
 * it back to the callback with a code and that {@code state}. The callback only goes ahead when the two match, so
 * another site can't complete a sign-in it didn't start in this browser. Whatever goes wrong, the browser lands on the
 * sign-in page with {@code ?error=google_failed}; the reason is logged only.
 */
@RestController
@Tag(name = "Google sign-in")
class GoogleSignInController {

    private static final Logger log = LoggerFactory.getLogger(GoogleSignInController.class);

    static final String STATE_COOKIE = "dokiments-oauth-state";
    static final String STATE_COOKIE_PATH = "/api/auth/google";
    static final URI SIGN_IN_FAILED = URI.create("/sign-in?error=google_failed");

    /** The landing page's query parameter with what happened to a promo code, which the web app shows a banner for. */
    static final String PROMO_PARAM = "promo";

    private final SignInWithGoogle google;
    private final SessionStore sessions;
    private final SessionCookies.Settings cookies;
    private final PromoAtSignIn promo;

    GoogleSignInController(
            SignInWithGoogle google, SessionStore sessions, SessionCookies.Settings cookies, PromoAtSignIn promo) {
        this.google = google;
        this.sessions = sessions;
        this.cookies = cookies;
        this.promo = promo;
    }

    @GetMapping("/api/auth/google/start")
    @Operation(
            summary = "Start signing in with Google",
            description =
                    "Redirects to Google's sign-in page. next is where to land afterwards, a path on this site"
                            + " (the dashboard otherwise). A promo code is kept with the sign-in and redeemed once it completes.")
    @ApiResponse(
            responseCode = "302",
            description = "To Google, or to /sign-in?error=google_failed when Google sign-in isn't set up")
    ResponseEntity<Void> start(
            @Parameter(description = "Where to land afterwards, for example /my-documents")
                    @RequestParam(defaultValue = "")
                    String next,
            @Parameter(description = "A promo code typed on the sign-in page") @RequestParam(defaultValue = "")
                    String promoCode) {
        SignInWithGoogle.Started started;
        try {
            started = google.start(next, promoCode);
        } catch (SignInWithGoogle.Failed e) {
            log.warn("Google sign-in couldn't start: {}", e.getMessage());
            return redirect(SIGN_IN_FAILED, null);
        }
        return redirect(started.authorizationUrl(), stateCookie(started.state(), PendingSignIns.LIFETIME));
    }

    @GetMapping("/api/auth/google/callback")
    @Operation(
            summary = "Finish signing in with Google",
            description = "Where Google sends the browser back. Starts the session (the dokiments-session and"
                    + " dokiments-session-expiry cookies, as for sign-in) and redirects to the page asked for at the"
                    + " start. The first Google sign-in creates the account; one with the address of a password"
                    + " account signs in to that account. When a promo code was given at the start, the page gets"
                    + " ?promo= with what happened to it: applied, already_redeemed, invalid or failed.")
    @ApiResponse(
            responseCode = "302",
            description = "To the page asked for, or to /sign-in?error=google_failed when anything went wrong")
    ResponseEntity<Void> callback(
            @RequestParam(defaultValue = "") String code,
            @RequestParam(defaultValue = "") String state,
            @RequestParam(defaultValue = "") String error,
            @CookieValue(name = STATE_COOKIE, defaultValue = "") String expectedState,
            HttpServletRequest request,
            HttpServletResponse response) {
        // The state cookie has done its job either way, so it goes on every answer.
        String clearState = stateCookie("", Duration.ZERO);
        if (!error.isEmpty()) {
            // Google's answer when the user cancels or refuses consent.
            log.info("Google sign-in was cancelled or refused: {}", error);
            return redirect(SIGN_IN_FAILED, clearState);
        }
        if (code.isEmpty() || state.isEmpty() || !state.equals(expectedState)) {
            log.warn(
                    "Google sign-in callback refused: code {}, state {}, state cookie {}",
                    code.isEmpty() ? "missing" : "present",
                    state.isEmpty() ? "missing" : "present",
                    expectedState.isEmpty() ? "missing" : (expectedState.equals(state) ? "matching" : "not matching"));
            return redirect(SIGN_IN_FAILED, clearState);
        }
        try {
            var finished = google.finish(state, code);
            // The account is proven now, so the code can be redeemed for it. A redirect can't carry the outcome in a
            // body, so it goes on the landing page's address, as in the web app.
            var outcome = promo.redeemAtSignIn(finished.session().uid(), finished.promoCode());
            sessions.start(request, response, finished.session());
            return redirect(landing(finished.returnPath(), outcome.orElse(null)), clearState);
        } catch (SignInWithGoogle.Failed e) {
            log.warn("Google sign-in didn't complete", e);
            return redirect(SIGN_IN_FAILED, clearState);
        }
    }

    /**
     * Where the browser lands: the return path, with the promo code's outcome added to its query when there is one. Any
     * {@code promo} parameter the path already had is replaced, so the outcome can't be faked by a link.
     */
    static URI landing(ReturnPath returnPath, @Nullable PromoOutcome outcome) {
        if (outcome == null) {
            return URI.create(returnPath.path());
        }
        return URI.create(UriComponentsBuilder.fromUriString(returnPath.path())
                .replaceQueryParam(PROMO_PARAM, outcome.value())
                .build()
                .toUriString());
    }

    /**
     * A redirect. A path, such as {@code /dashboard}, stays relative, so the browser resolves it against the site it is
     * on: the web app's, which forwards these routes here.
     */
    private static ResponseEntity<Void> redirect(URI location, @Nullable String cookie) {
        var answer = ResponseEntity.status(HttpStatus.FOUND).location(location);
        if (cookie != null) {
            answer.header(HttpHeaders.SET_COOKIE, cookie);
        }
        return answer.build();
    }

    /**
     * The {@code state} for the callback. HttpOnly; sent only to the Google sign-in routes; {@code SameSite=Lax} so
     * Google's redirect back, a navigation from another site, still carries it.
     */
    private String stateCookie(String value, Duration maxAge) {
        return ResponseCookie.from(STATE_COOKIE, value)
                .httpOnly(true)
                .secure(cookies.secureCookies())
                .sameSite("Lax")
                .path(STATE_COOKIE_PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
