package com.dokiments.users.application.port.out;

import com.dokiments.users.ProfileChanges;
import com.dokiments.users.UserProfile;
import java.time.Instant;
import java.util.Optional;

/** Where profiles are kept. */
public interface UserProfileStore {

    Optional<UserProfile> find(String uid);

    /** Stores a new profile, unless one already exists for its ID, which then stays as it is. */
    void createIfAbsent(UserProfile profile, Instant createdAt);

    /** Stores the changed fields, and only those, with the time of the change. */
    void update(String uid, ProfileChanges changes, Instant updatedAt);
}
