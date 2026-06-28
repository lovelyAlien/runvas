package com.runvas.global.security;

import com.runvas.auth.service.JwtProvider;
import io.jsonwebtoken.JwtException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityConfigTest.TestController.class)
@Import({
        SecurityConfig.class,
        SecurityErrorResponseWriter.class,
        RunvasAuthenticationEntryPoint.class,
        RunvasAccessDeniedHandler.class,
        SecurityConfigTest.TestController.class
})
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtProvider jwtProvider;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingCredentialsReturnUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(get("/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Authentication is required"))
                .andExpect(jsonPath("$.error.details.length()").value(0));
    }

    @Test
    void invalidBearerTokenReturnsUnauthorizedEnvelopeAndClearsContext() throws Exception {
        when(jwtProvider.parseUserId("bad-token")).thenThrow(new JwtException("invalid token"));

        mockMvc.perform(get("/test/protected")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Authentication is required"))
                .andExpect(jsonPath("$.error.details.length()").value(0));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validBearerTokenAuthenticatesRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.parseUserId("valid-token")).thenReturn(userId);

        mockMvc.perform(get("/test/protected")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/protected")
        Map<String, String> protectedEndpoint(Authentication authentication) {
            RunvasPrincipal principal = (RunvasPrincipal) authentication.getPrincipal();
            return Map.of("userId", principal.userId().toString());
        }
    }
}
