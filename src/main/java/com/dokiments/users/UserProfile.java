package com.dokiments.users;

import org.jspecify.annotations.Nullable;

/**
 * Someone's profile: who they are in the application and what they may do.
 *
 * @param uid the Firebase Auth ID, which is also the profile's document ID
 * @param email the address they signed up with
 * @param displayName the name shown throughout the app
 * @param role their plan or privileges
 * @param provider how the account was first created
 * @param linkedGoogle whether a Google sign-in has been linked to an account first created with a password
 * @param details optional business and contact details
 */
public record UserProfile(
        String uid,
        String email,
        String displayName,
        UserRole role,
        AuthProvider provider,
        boolean linkedGoogle,
        Details details) {

    /** Optional information someone can add in their settings. Each is absent until set. */
    public record Details(
            @Nullable String address,
            @Nullable String brn,
            @Nullable String businessEmail,
            @Nullable String companyName,
            @Nullable String fullName,
            @Nullable String phone,
            @Nullable String tel,
            @Nullable String website) {

        public static final Details NONE = new Details(null, null, null, null, null, null, null, null);
    }
}
