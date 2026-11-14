package com.dokiments.users;

import org.jspecify.annotations.Nullable;

/** Profiles, for the modules that need to know who someone is. */
public interface UserProfiles {

    /**
     * The account's current role, as stored, so a change of plan counts straight away rather than at the next sign-in.
     * {@link UserRole#FREE} for an account without a profile.
     *
     * @throws com.dokiments.shared.error.UpstreamUnavailableException if the profile couldn't be read
     */
    UserRole roleOf(String uid);

    /**
     * The profile of a signed-in account, created on its first sign-in.
     *
     * <p>A new profile always gets the {@link UserRole#FREE} role, and {@code displayName}, or the part of the address
     * before the {@code @} when it is blank. An existing profile is returned as stored: these arguments only seed a new
     * one. The one exception: the first Google sign-in to an account created with a password marks it as linked to
     * Google ({@code linkedGoogle}); its {@code provider} stays {@code password}.
     *
     * @throws com.dokiments.shared.error.UpstreamUnavailableException if the profile couldn't be read or stored
     */
    UserProfile findOrCreate(String uid, String email, @Nullable String displayName, AuthProvider provider);

    /**
     * Stores what someone changed in their settings, and returns the profile as it is now. Three things never change
     * this way: the sign-in address, the role and how the account was created.
     *
     * @throws com.dokiments.shared.error.UpstreamUnavailableException if the profile couldn't be stored
     */
    UserProfile updateProfile(UserProfile current, ProfileChanges changes);
}
