package com.dokiments.sharing.domain;

import java.time.Instant;

/**
 * A recipient's first opening of a shared copy.
 *
 * @param email their address, lower-case
 * @param viewedAt when they first opened it
 */
public record View(String email, Instant viewedAt) {}
