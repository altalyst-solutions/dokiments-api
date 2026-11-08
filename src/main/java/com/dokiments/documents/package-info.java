/**
 * A user's own documents: create, list, read, update and delete.
 *
 * <p>Hexagonal layout inside this module: {@code domain} (pure Java), {@code application} (use cases and ports),
 * {@code adapter} (web, persistence, external services).
 */
@NullMarked
@ApplicationModule(displayName = "Documents")
package com.dokiments.documents;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
