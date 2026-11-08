/**
 * The template catalog, template styles and a user's saved templates.
 *
 * <p>Hexagonal layout inside this module: {@code domain} (pure Java), {@code application} (use cases and ports),
 * {@code adapter} (web, persistence, external services).
 */
@NullMarked
@ApplicationModule(displayName = "Templates")
package com.dokiments.templates;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
