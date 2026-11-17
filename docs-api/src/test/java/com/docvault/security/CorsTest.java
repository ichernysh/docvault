package com.docvault.security;

import com.docvault.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the browser needs from the API so that the SPA on another origin can call it.
 * The browser sends a preflight (OPTIONS) first, and it never carries a token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class CorsTest {

    private static final String SPA_ORIGIN = "http://localhost:3000";

    @Autowired MockMvc mvc;

    @Test
    void preflightFromSpaIsAnsweredWithoutToken() throws Exception {
        mvc.perform(options("/api/documents")
                        .header(HttpHeaders.ORIGIN, SPA_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SPA_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsStringIgnoringCase("authorization")));
    }

    @Test
    void preflightFromForeignOriginIsRejected() throws Exception {
        mvc.perform(options("/api/documents")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void realRequestFromSpaCarriesTheAllowHeader() throws Exception {
        mvc.perform(get("/api/documents")
                        .header(HttpHeaders.ORIGIN, SPA_ORIGIN)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                        .claim("tenant_id", "acme")
                                        .claim("realm_access", Map.of("roles", List.of("viewer"))))
                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SPA_ORIGIN));
    }

    @Test
    void corsDoesNotOpenTheApiWithoutToken() throws Exception {
        mvc.perform(get("/api/documents").header(HttpHeaders.ORIGIN, SPA_ORIGIN))
                .andExpect(status().isUnauthorized());
    }
}
