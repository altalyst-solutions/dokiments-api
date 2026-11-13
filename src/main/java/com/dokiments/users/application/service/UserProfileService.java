package com.dokiments.users.application.service;

import com.dokiments.users.AuthProvider;
import com.dokiments.users.ProfileChanges;
import com.dokiments.users.UserProfile;
import com.dokiments.users.UserProfiles;
import com.dokiments.users.UserRole;
import com.dokiments.users.application.port.out.UserProfileStore;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
class UserProfileService implements UserProfiles {

    private final UserProfileStore store;
    private final Clock clock;

    UserProfileService(UserProfileStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public UserProfile findOrCreate(String uid, String email, @Nullable String displayName, AuthProvider provider) {
        var existing = store.find(uid);
        if (existing.isPresent()) {
            return linkedIfFirstGoogleSignIn(existing.get(), provider);
        }
        store.createIfAbsent(newProfile(uid, email, displayName, provider), clock.instant());
        // Read back rather than returning what was written: if two sign-ins of a new account raced, the one that lost
        // returns the profile the other created.
        return store.find(uid).orElseThrow(() -> new IllegalStateException("The new profile wasn't stored"));
    }

    @Override
    public UserProfile updateProfile(UserProfile current, ProfileChanges changes) {
        store.update(current.uid(), changes, clock.instant());
        // As the web app answers: the changed fields as sent, empty ones as empty text.
        return new UserProfile(
                current.uid(),
                current.email(),
                changes.displayName(),
                current.role(),
                current.provider(),
                current.linkedGoogle(),
                new UserProfile.Details(
                        changes.address(),
                        changes.brn(),
                        changes.businessEmail(),
                        changes.companyName(),
                        changes.fullName(),
                        changes.phone(),
                        changes.tel(),
                        changes.website()));
    }

    /**
     * Firebase signs a Google sign-in with the address of a password account in to that same account. The first time,
     * the profile notes it, so the settings can say Google is connected too. Purely informational: it never decides
     * what someone may do.
     */
    private UserProfile linkedIfFirstGoogleSignIn(UserProfile profile, AuthProvider signedInWith) {
        if (signedInWith != AuthProvider.GOOGLE
                || profile.provider() != AuthProvider.PASSWORD
                || profile.linkedGoogle()) {
            return profile;
        }
        store.markGoogleLinked(profile.uid());
        return new UserProfile(
                profile.uid(),
                profile.email(),
                profile.displayName(),
                profile.role(),
                profile.provider(),
                true,
                profile.details());
    }

    static UserProfile newProfile(String uid, String email, @Nullable String displayName, AuthProvider provider) {
        String name = displayName == null || displayName.isBlank() ? localPart(email) : displayName;
        return new UserProfile(uid, email, name, UserRole.FREE, provider, false, UserProfile.Details.NONE);
    }

    /** {@code jane} for {@code jane@example.com}: the name a profile gets when none is given. */
    static String localPart(String email) {
        int at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }
}
