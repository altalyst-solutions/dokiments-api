/**
 * Public newsletter sign-up.
 *
 * <p>Hexagonal layout inside this module: {@code domain} (pure Java), {@code application} (use cases and ports),
 * {@code adapter} (web, persistence, external services).
 */
@NullMarked
@ApplicationModule(displayName = "Newsletter")
package com.dokiments.newsletter;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
