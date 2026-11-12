package com.dokiments.platform.web;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Makes the API read and write JSON only, as the web app's API does.
 *
 * <p>Libraries on the classpath (the Google and Firebase clients) bring Jackson's XML, YAML, Smile and CBOR modules,
 * and Spring would then also answer in those formats when a client asks for them. A browser opening an API address
 * directly asks for XML before anything else, so it would get {@code <ErrorBody>...} instead of the {@code { "error":
 * "..." }} the web app expects. Removing those converters leaves JSON for every client, whatever its {@code Accept}
 * header. Plain text and binary converters stay, for endpoints that return a file.
 */
@Configuration(proxyBeanMethods = false)
class JsonOnlyConfiguration implements WebMvcConfigurer {

    private static final List<MediaType> OTHER_DATA_FORMATS = List.of(
            MediaType.APPLICATION_XML,
            MediaType.TEXT_XML,
            MediaType.APPLICATION_YAML,
            MediaType.APPLICATION_CBOR,
            MediaType.parseMediaType("application/x-jackson-smile"));

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(
                converters -> converters.removeIf(JsonOnlyConfiguration::isOtherDataFormat));
    }

    static boolean isOtherDataFormat(HttpMessageConverter<?> converter) {
        return converter.getSupportedMediaTypes().stream()
                .anyMatch(
                        type -> OTHER_DATA_FORMATS.stream().anyMatch(type::isCompatibleWith) && !type.isWildcardType());
    }
}
