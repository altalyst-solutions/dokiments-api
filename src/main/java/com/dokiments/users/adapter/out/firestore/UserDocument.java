package com.dokiments.users.adapter.out.firestore;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.users.AuthProvider;
import com.dokiments.users.ProfileChanges;
import com.dokiments.users.UserProfile;
import com.dokiments.users.UserRole;
import com.google.cloud.firestore.DocumentSnapshot;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The stored shape of a profile, {@code users/{uid}}. Field names are fixed by the documents that already exist.
 *
 * <p>Reading is forgiving, as the web app's is: a missing name falls back to the part of the address before the
 * {@code @}, an unknown role to {@code free}, a missing provider to {@code password}, and empty optional details count
 * as absent.
 */
final class UserDocument {

    private UserDocument() {}

    static UserProfile read(String uid, DocumentSnapshot snapshot) {
        String email = text(snapshot, "email");
        String storedEmail = email == null ? "" : email;
        String displayName = text(snapshot, "displayName");
        return new UserProfile(
                uid,
                storedEmail,
                displayName == null ? localPart(storedEmail) : displayName,
                UserRole.fromValue(String.valueOf(text(snapshot, "role"))),
                AuthProvider.fromValue(String.valueOf(text(snapshot, "provider"))),
                Boolean.TRUE.equals(snapshot.getBoolean("linkedGoogle")),
                new UserProfile.Details(
                        optional(snapshot, "address"),
                        optional(snapshot, "brn"),
                        optional(snapshot, "businessEmail"),
                        optional(snapshot, "companyName"),
                        optional(snapshot, "fullName"),
                        optional(snapshot, "phone"),
                        optional(snapshot, "tel"),
                        optional(snapshot, "website")));
    }

    /** The fields a new profile is created with, as the web app creates it. */
    static Map<String, Object> forNewProfile(UserProfile profile, Instant createdAt) {
        var timestamp = FirestoreCalls.toTimestamp(createdAt);
        return Map.of(
                "createdAt", timestamp,
                "updatedAt", timestamp,
                "displayName", profile.displayName(),
                "email", profile.email(),
                "provider", profile.provider().value(),
                "role", profile.role().value());
    }

    /** The fields a change in settings writes. Empty ones are stored as empty text, as the web app stores them. */
    static Map<String, Object> forChanges(ProfileChanges changes, Instant updatedAt) {
        return Map.of(
                "displayName", changes.displayName(),
                "address", changes.address(),
                "brn", changes.brn(),
                "businessEmail", changes.businessEmail(),
                "companyName", changes.companyName(),
                "fullName", changes.fullName(),
                "phone", changes.phone(),
                "tel", changes.tel(),
                "website", changes.website(),
                "updatedAt", FirestoreCalls.toTimestamp(updatedAt));
    }

    private static @Nullable String text(DocumentSnapshot snapshot, String field) {
        return snapshot.get(field) instanceof String value ? value : null;
    }

    private static @Nullable String optional(DocumentSnapshot snapshot, String field) {
        String value = text(snapshot, field);
        return value == null || value.isEmpty() ? null : value;
    }

    private static String localPart(String email) {
        int at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }
}
