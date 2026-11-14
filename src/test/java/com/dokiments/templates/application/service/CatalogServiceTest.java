package com.dokiments.templates.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.templates.TemplateSnapshot;
import com.dokiments.templates.domain.Catalog;
import com.dokiments.templates.domain.Template;
import com.dokiments.templates.domain.TemplateField;
import com.dokiments.templates.domain.TemplateStyle;
import com.dokiments.templates.domain.TemplateTier;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {

    private static final TemplateStyle MODERN = new TemplateStyle("modern", "Modern", "Bold", TemplateTier.SILVER);
    private final CatalogService service = new CatalogService(() -> new Catalog(
            List.of(MODERN),
            List.of(new Template(
                    "modern-invoice",
                    "Invoice",
                    "A billing sheet",
                    "invoice",
                    List.of(
                            new TemplateField("client", "Client", "Acme Ltd", "text"),
                            new TemplateField("due", "Due", null, "date")),
                    MODERN,
                    TemplateTier.SILVER,
                    null))));

    @Test
    void aTemplateOfTheCatalogGivesItsContentToCopy() {
        assertThat(service.snapshot("modern-invoice"))
                .contains(new TemplateSnapshot(
                        "Invoice",
                        "A billing sheet",
                        "invoice",
                        List.of(
                                new TemplateSnapshot.Field("client", "Client", "Acme Ltd", "text"),
                                new TemplateSnapshot.Field("due", "Due", null, "date")),
                        new TemplateSnapshot.Style("modern", "Modern", "Bold", "silver")));
        assertThat(service.snapshot("modern-nope")).isEmpty();
    }
}
