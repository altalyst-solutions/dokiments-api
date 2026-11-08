/**
 * Sign-up, sign-in (email/password and Google), sessions, email verification and password management, backed by
 * Firebase Auth.
 *
 * <p>Hexagonal layout inside this module: {@code domain} (pure Java), {@code application} (use cases and ports),
 * {@code adapter} (web, persistence, external services).
 */
@NullMarked
@ApplicationModule(displayName = "Auth")
package com.dokiments.auth;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
