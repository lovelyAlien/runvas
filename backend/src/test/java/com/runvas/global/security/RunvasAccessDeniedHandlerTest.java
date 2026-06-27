package com.runvas.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class RunvasAccessDeniedHandlerTest {

    @Test
    void accessDeniedReturnsForbiddenEnvelope() throws Exception {
        RunvasAccessDeniedHandler handler = new RunvasAccessDeniedHandler(
                new SecurityErrorResponseWriter(new ObjectMapper())
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(response.getContentAsString()).contains("\"message\":\"Access is forbidden\"");
        assertThat(response.getContentAsString()).contains("\"details\":[]");
    }
}
