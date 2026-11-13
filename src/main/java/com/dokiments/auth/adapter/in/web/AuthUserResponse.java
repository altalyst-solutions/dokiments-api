package com.dokiments.auth.adapter.in.web;

import com.dokiments.auth.domain.AuthSession;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * The signed-in user, as the web app's {@code AuthUser}. Tokens are never part of it. Details that aren't set are left
 * out, as the web app leaves them out.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record AuthUserResponse(
        String uid,
        String email,
        String displayName,

        @Schema(description = "Whether Firebase has confirmed the address.")
        boolean emailVerified,

        @Schema(allowableValues = {"password", "google"}) String provider,

        @Schema(allowableValues = {"superadmin", "free", "silver", "gold", "special"})
        String role,

        boolean linkedGoogle,
        @Nullable String address,
        @Nullable String brn,
        @Nullable String businessEmail,
        @Nullable String companyName,
        @Nullable String fullName,
        @Nullable String phone,
        @Nullable String tel,
        @Nullable String website) {

    static AuthUserResponse of(AuthSession session) {
        var user = session.user();
        var details = user.details();
        return new AuthUserResponse(
                user.uid(),
                user.email(),
                user.displayName(),
                session.emailVerified(),
                user.provider().value(),
                user.role().value(),
                user.linkedGoogle(),
                details.address(),
                details.brn(),
                details.businessEmail(),
                details.companyName(),
                details.fullName(),
                details.phone(),
                details.tel(),
                details.website());
    }
}
