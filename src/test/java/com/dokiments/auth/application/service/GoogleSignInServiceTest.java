package com.dokiments.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.auth.application.port.in.SignInWithGoogle;
import com.dokiments.auth.application.port.out.GoogleOAuth;
import com.dokiments.auth.application.port.out.PendingSignIns;
import com.dokiments.auth.domain.ReturnPath;
import com.dokiments.users.AuthProvider;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

// The ports it fakes are null-marked, like the production code.
@NullMarked
class GoogleSignInServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

    private final Map<String, ReturnPath> pending = new HashMap<>();
    private final PendingSignIns store = new PendingSignIns() {
        @Override
        public void save(String state, ReturnPath returnPath) {
            pending.put(state, returnPath);
        }

        @Override
        public Optional<ReturnPath> take(String state) {
            return Optional.ofNullable(pending.remove(state));
        }
    };
    private boolean configured = true;
    private boolean googleRefuses;
    private final GoogleOAuth google = new GoogleOAuth() {
        @Override
        public boolean configured() {
            return configured;
        }

        @Override
        public URI authorizationUrl(String state) {
            return URI.create("https://google.test/auth?state=" + state);
        }

        @Override
        public String exchangeCode(String code) {
            if (googleRefuses) {
                throw new Refused("Google refused the code: 400");
            }
            return "google-id-token-for-" + code;
        }
    };
    private final AuthServiceTest.FakeFirebase firebase = new AuthServiceTest.FakeFirebase();
    private final AuthServiceTest.FakeProfiles profiles = new AuthServiceTest.FakeProfiles();
    private final GoogleSignInService service =
            new GoogleSignInService(google, store, firebase, profiles, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aStartRemembersWhereToGoUnderARandomState() {
        var started = service.start("/my-documents");

        assertThat(started.authorizationUrl().toString()).endsWith("state=" + started.state());
        assertThat(pending).containsEntry(started.state(), new ReturnPath("/my-documents"));
        assertThat(service.start(null).state()).isNotEqualTo(started.state());
    }

    @Test
    void withoutGoogleSettingsTheStartFails() {
        configured = false;

        assertThatThrownBy(() -> service.start(null)).isInstanceOf(SignInWithGoogle.Failed.class);
    }

    @Test
    void aFinishedSignInStartsADayLongGoogleSession() {
        var started = service.start("/my-documents");

        var finished = service.finish(started.state(), "the-code");

        assertThat(finished.returnPath()).isEqualTo(new ReturnPath("/my-documents"));
        assertThat(finished.session().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(profiles.created).singleElement().asString().endsWith(AuthProvider.GOOGLE.name());
        assertThat(firebase.googleTokens).containsExactly("google-id-token-for-the-code");
    }

    @Test
    void aStateCanOnlyFinishOneSignIn() {
        var started = service.start(null);
        service.finish(started.state(), "the-code");

        assertThatThrownBy(() -> service.finish(started.state(), "the-code"))
                .isInstanceOf(SignInWithGoogle.Failed.class);
    }

    @Test
    void anUnknownStateFails() {
        assertThatThrownBy(() -> service.finish("made-up", "the-code")).isInstanceOf(SignInWithGoogle.Failed.class);
    }

    @Test
    void aCodeGoogleRefusesFails() {
        googleRefuses = true;
        var started = service.start(null);

        assertThatThrownBy(() -> service.finish(started.state(), "old-code"))
                .isInstanceOf(SignInWithGoogle.Failed.class)
                .hasCauseInstanceOf(GoogleOAuth.Refused.class);
    }
}
