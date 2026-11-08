/**
 * User profiles, roles and plan limits.
 *
 * <p>Hexagonal layout inside this module: {@code domain} (pure Java), {@code application} (use cases and ports),
 * {@code adapter} (web, persistence, external services).
 */
@NullMarked
@ApplicationModule(displayName = "Users")
package com.dokiments.users;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
