package com.docvault;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full path with a real Keycloak started from keycloak/realm-docvault.json (the same file docker compose imports):
 * real token, real signature check against real JWKS, real iss/aud validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
@Testcontainers
class KeycloakAuthorizationIT {

    @Container
    static final KeycloakContainer KEYCLOAK =
            new KeycloakContainer("quay.io/keycloak/keycloak:26.8.2").withRealmImportFile("realm-docvault.json");

    /**
     * Realm default is sslRequired=external: plain HTTP is allowed only from "local" addresses. Docker Desktop port
     * forwarding can make our requests look external ("HTTPS required"), so for the test we switch it off from inside
     * the container (loopback there). The shared realm-docvault.json stays untouched.
     */
    @BeforeAll
    static void allowPlainHttp() throws Exception {
        String kcadm = "/opt/keycloak/bin/kcadm.sh";
        var login = KEYCLOAK.execInContainer(kcadm, "config", "credentials", "--server", "http://localhost:8080",
                "--realm", "master", "--user", KEYCLOAK.getAdminUsername(), "--password", KEYCLOAK.getAdminPassword());
        assertThat(login.getExitCode()).as(login.getStderr()).isZero();
        var update = KEYCLOAK.execInContainer(kcadm, "update", "realms/docvault", "-s", "sslRequired=NONE");
        assertThat(update.getExitCode()).as(update.getStderr()).isZero();
    }

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        String issuer = KEYCLOAK.getAuthServerUrl() + "/realms/docvault";
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuer);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> issuer + "/protocol/openid-connect/certs");
    }

    @Autowired MockMvc mvc;

    /** Password grant on the dev client `web` (what scripts/get-token.sh does). */
    private static String tokenFor(String username) throws Exception {
        String form = "client_id=web&grant_type=password&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=password";
        var request = HttpRequest.newBuilder(URI.create(KEYCLOAK.getAuthServerUrl()
                        + "/realms/docvault/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("token for " + username + ": " + response.body()).isEqualTo(200);
        return new ObjectMapper().readTree(response.body()).get("access_token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void realTokenOfEditorCanCreateAndViewerCannot() throws Exception {
        mvc.perform(post("/api/documents").header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("alice")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"From Keycloak\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/documents").header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("bob")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantsAreIsolatedWithRealTokens() throws Exception {
        String body = mvc.perform(post("/api/documents").header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("alice")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Acme only\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = new ObjectMapper().readTree(body).get("id").asText();

        // carol is an admin of ANOTHER tenant (globex)
        mvc.perform(get("/api/documents/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("carol"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenWithTamperedPayloadIsRejected() throws Exception {
        String[] parts = tokenFor("alice").split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"acme\"", "\"globex\"");
        String forged = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        mvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        mvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }
}
