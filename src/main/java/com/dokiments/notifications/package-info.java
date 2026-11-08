/**
 * In-app notifications and their read state.
 *
 * <p>Hexagonal layout inside this module: {@code domain} (pure Java), {@code application} (use cases and ports),
 * {@code adapter} (web, persistence, external services).
 */
@NullMarked
@ApplicationModule(displayName = "Notifications")
package com.dokiments.notifications;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
