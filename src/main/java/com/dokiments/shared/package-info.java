/**
 * Shared kernel: small, stable value types and abstractions every module may depend on (e.g. {@code UserId},
 * {@code Email}). Keep it minimal; anything feature-specific belongs in its own module.
 */
@NullMarked
@ApplicationModule(displayName = "Shared kernel")
package com.dokiments.shared;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
