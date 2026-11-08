/**
 * A user's saved clients.
 *
 * <p>Hexagonal layout inside this module: {@code domain} (pure Java), {@code application} (use cases and ports),
 * {@code adapter} (web, persistence, external services).
 */
@NullMarked
@ApplicationModule(displayName = "Clients")
package com.dokiments.clients;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
