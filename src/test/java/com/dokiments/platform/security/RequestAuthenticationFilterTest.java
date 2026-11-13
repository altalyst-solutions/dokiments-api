package com.dokiments.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.web.ErrorResponseWriter;
import com.dokiments.shared.error.UpstreamUnavailableException;
import java.net.ConnectException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/** How the answer of a {@link RequestAuthenticator} reaches the rest of the request. */
class RequestAuthenticationFilterTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();
    private final ErrorResponseWriter writer =
            new ErrorResponseWriter(JsonMapper.builder().build());

    @AfterEach
    void forgetTheUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aSignedInUserIsRecordedForTheRequest() throws Exception {
        var jane = UsernamePasswordAuthenticationToken.authenticated("jane", null, List.of());
        new RequestAuthenticationFilter(List.of((_, _) -> jane), writer).doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(jane);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void noUserLeavesTheRequestAnonymous() throws Exception {
        new RequestAuthenticationFilter(List.of((_, _) -> null), writer).doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void aServiceThatCantBeReachedAnswers503WithoutGoingFurther() throws Exception {
        RequestAuthenticator unreachable = (_, _) -> {
            throw new UpstreamUnavailableException(new ConnectException("refused"));
        };

        new RequestAuthenticationFilter(List.of(unreachable), writer).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"error\":\"Couldn't reach an external service. Please try again in a moment.\"}");
        assertThat(chain.getRequest()).isNull();
    }
}
