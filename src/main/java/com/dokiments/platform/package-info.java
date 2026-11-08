/**
 * Technical plumbing with no business rules: security, configuration, web error handling, Firestore and email
 * infrastructure. Feature modules use what it exposes; it never depends on them.
 */
@NullMarked
@ApplicationModule(displayName = "Platform", type = ApplicationModule.Type.OPEN)
package com.dokiments.platform;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
