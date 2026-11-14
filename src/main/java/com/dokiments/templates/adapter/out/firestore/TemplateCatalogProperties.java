package com.dokiments.templates.adapter.out.firestore;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How the catalog is read.
 *
 * @param catalogCacheTime how long the catalog is reused before it is read from Firestore again. It is the same for
 *     every user and changes rarely, so reading it on every request would only cost reads. Zero reads it every time,
 *     which the {@code dev} profile and the tests use so a change to the catalog shows at once.
 */
@ConfigurationProperties("dokiments.templates")
record TemplateCatalogProperties(@DefaultValue("5m") Duration catalogCacheTime) {}
