package com.dokiments.auth;

import com.dokiments.auth.domain.AuthSession;
import com.dokiments.auth.domain.AuthTokens;
import com.dokiments.users.AuthProvider;
import com.dokiments.users.UserProfile;
import com.dokiments.users.UserRole;
import java.time.Instant;

/** Ready-made profiles and sessions for auth tests. */
public final class TestUsers {

    private TestUsers() {}

    public static UserProfile jane(UserRole role) {
        return new UserProfile(
                "uid-jane", "jane@example.com", "Jane", role, AuthProvider.PASSWORD, false, UserProfile.Details.NONE);
    }

    public static AuthTokens tokens(String name, Instant idTokenExpiresAt) {
        return new AuthTokens("id-" + name, "refresh-" + name, idTokenExpiresAt);
    }

    public static AuthSession session(Instant signedInAt, Instant idTokenExpiresAt) {
        return AuthSession.start(jane(UserRole.FREE), false, tokens("first", idTokenExpiresAt), signedInAt);
    }
}
