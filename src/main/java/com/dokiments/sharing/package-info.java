/**
 * Sharing documents for signature: share, view tracking, signing, revoking and the related emails.
 *
 * <p>Hexagonal layout inside this module: {@code domain} (pure Java), {@code application} (use cases and ports),
 * {@code adapter} (web, persistence, external services).
 */
@NullMarked
@ApplicationModule(displayName = "Sharing")
package com.dokiments.sharing;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
