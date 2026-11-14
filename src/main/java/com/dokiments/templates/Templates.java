package com.dokiments.templates;

import java.util.Optional;

/** The template catalog, for the modules that make something from a template. */
public interface Templates {

    /**
     * The content of a template in the catalog, as it is now.
     *
     * @return empty when the template isn't in the catalog, or is hidden there
     * @throws com.dokiments.shared.error.UpstreamUnavailableException if the catalog couldn't be read
     */
    Optional<TemplateSnapshot> snapshot(String templateId);
}
