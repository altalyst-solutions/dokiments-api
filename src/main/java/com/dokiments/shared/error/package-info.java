/**
 * Exceptions that describe what went wrong in terms every module understands (not found, forbidden, conflict, ...),
 * each carrying a message that is safe to show to the user.
 *
 * <p>They are plain Java, so domain code can throw them. Turning them into HTTP responses is done in one place, by
 * {@code com.dokiments.platform.web.ApiExceptionHandler}. A module that needs a more specific error, such as "this
 * document is locked", extends the matching type here (in that case
 * {@link com.dokiments.shared.error.ConflictException}) so it gets the right status code without any web code of its
 * own.
 *
 * <p>Declared a named interface, so other modules may use it: sub-packages of a module are otherwise internal to it.
 */
@NullMarked
@NamedInterface("error")
package com.dokiments.shared.error;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
