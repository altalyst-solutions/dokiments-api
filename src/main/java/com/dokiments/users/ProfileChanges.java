package com.dokiments.users;

/**
 * What someone can change in their settings. Every field is set: an empty one clears it.
 *
 * @param displayName the name shown throughout the app, never empty
 */
public record ProfileChanges(
        String displayName,
        String address,
        String brn,
        String businessEmail,
        String companyName,
        String fullName,
        String phone,
        String tel,
        String website) {}
