package com.dokiments.platform.web;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The body of every error response: {@code { "error": "..." }}.
 *
 * <p>The web app reads exactly these fields, so no others (timestamps, paths, exception names) are added. The message
 * must always be safe to show to a user. A {@code code} appears only for the few failures the web app handles in code,
 * and extra fields only for the few where it shows or uses more than the message (see
 * {@code DokimentsException.publicFields}).
 *
 * @param error message for the user
 * @param code a code the web app acts on, such as {@code REAUTH_REQUIRED}; left out when there is none
 * @param fields extra fields, written next to {@code error}, such as {@code invitedEmail}; usually none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(
        String error, @Nullable String code, @JsonIgnore Map<String, String> fields) {

    public ErrorBody {
        fields = Map.copyOf(fields);
    }

    public ErrorBody(String error) {
        this(error, null, Map.of());
    }

    /** The extra fields, written at the top level of the body rather than nested. */
    @JsonAnyGetter
    Map<String, String> extraFields() {
        return fields;
    }
}
