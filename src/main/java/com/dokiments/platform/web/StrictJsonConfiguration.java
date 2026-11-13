package com.dokiments.platform.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Text fields in request bodies accept only text, as the web app's request checks do.
 *
 * <p>Jackson would otherwise turn {@code 123} or {@code true} into the text {@code "123"} or {@code "true"}, so a
 * request the web app refuses with {@code 400} would be accepted here, and a number could end up stored where text is
 * expected.
 */
@Configuration(proxyBeanMethods = false)
class StrictJsonConfiguration {

    @Bean
    JsonMapperBuilderCustomizer textOnlyFromText() {
        return builder -> builder.withCoercionConfig(
                LogicalType.Textual,
                config -> config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
