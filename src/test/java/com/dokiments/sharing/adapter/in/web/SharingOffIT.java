package com.dokiments.sharing.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** While sharing is switched off, its routes don't exist: {@code 404} once signed in, as in the web app. */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "dokiments.features.document-sharing=false")
class SharingOffIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void everySharingRouteIsNotFoundWhileSharingIsOff() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MvcTestResult signedUp = call(
                HttpMethod.POST,
                "/api/auth/sign-up",
                "{\"displayName\":\"Jane\",\"email\":\"" + email + "\",\"password\":\"secret123\"}",
                null);
        Cookie session = signedUp.getResponse().getCookie("dokiments-session");
        assertThat(session).isNotNull();

        for (var call : List.of(
                call(HttpMethod.POST, "/api/documents/doc_x/share", "{\"email\":\"a@b.co\"}", session),
                call(HttpMethod.GET, "/api/documents/share-recipients", null, session),
                call(HttpMethod.GET, "/api/documents/shared", null, session),
                call(HttpMethod.GET, "/api/documents/shared?by=me", null, session),
                call(HttpMethod.GET, "/api/documents/shared/x_doc_x", null, session),
                call(HttpMethod.POST, "/api/documents/shared/x_doc_x/sign", "{\"signature\":\"Jane\"}", session),
                call(HttpMethod.POST, "/api/documents/shared/x_doc_x/revoke", null, session),
                call(HttpMethod.GET, "/api/shared-invites/" + "x".repeat(43), null, null),
                call(HttpMethod.POST, "/api/shared-invites/" + "x".repeat(43) + "/accept", null, session))) {
            assertThat(call).hasStatus(404).bodyJson().isStrictlyEqualTo("{\"error\":\"Not found.\"}");
        }
        assertThat(call(HttpMethod.GET, "/api/documents/shared", null, null)).hasStatus(401);
    }

    private MvcTestResult call(HttpMethod method, String path, @Nullable String body, @Nullable Cookie session) {
        Cookie csrf = mvc.get().uri("/api/auth/me").exchange().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        var request = mvc.method(method).uri(path).header("X-XSRF-TOKEN", csrf.getValue());
        request = session == null ? request.cookie(csrf) : request.cookie(csrf, session);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}
