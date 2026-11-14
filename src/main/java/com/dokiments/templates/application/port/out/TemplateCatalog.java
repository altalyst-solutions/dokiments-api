package com.dokiments.templates.application.port.out;

import com.dokiments.templates.domain.Catalog;

/** Where the catalog is kept. */
public interface TemplateCatalog {

    /** The catalog as it is now, or as it was a short while ago: it changes rarely, so it may be reused. */
    Catalog current();
}
