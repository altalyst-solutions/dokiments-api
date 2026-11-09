/**
 * Technical plumbing with no business rules: security, configuration, web error handling, Firestore, calls to external
 * services and email infrastructure. Feature modules use what it exposes; it never depends on them.
 */
@NullMarked
@ApplicationModule(displayName = "Platform", type = ApplicationModule.Type.OPEN)
package com.dokiments.platform;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
