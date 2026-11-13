package com.dokiments.users;

import org.jspecify.annotations.Nullable;

/** Profiles, for the modules that need to know who someone is. */
public interface UserProfiles {

    /**
     * The profile of a signed-in account, created on its first sign-in.
     *
     * <p>A new profile always gets the {@link UserRole#FREE} role, and {@code displayName}, or the part of the address
     * before the {@code @} when it is blank. An existing profile is returned as stored: these arguments only seed a new
     * one.
     *
     * @throws com.dokiments.shared.error.UpstreamUnavailableException if the profile couldn't be read or stored
     */
    UserProfile findOrCreate(String uid, String email, @Nullable String displayName, AuthProvider provider);
}
