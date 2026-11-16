package com.docvault.document;

import com.docvault.PostgresTestConfig;
import com.docvault.security.KeycloakRealmRoleConverter;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Authorization rules of the API. The JWT itself is faked with spring-security-test (no Keycloak),
 * but roles still go through our real {@link KeycloakRealmRoleConverter}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class DocumentControllerTest {

    @Autowired MockMvc mvc;

    /** A caller with a random id, so tests do not see each other's documents. */
    private static RequestPostProcessor user(String id, String tenant, String... roles) {
        return jwt()
                .jwt(j -> j.subject(id).claim("tenant_id", tenant).claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private String createDocument(RequestPostProcessor owner) throws Exception {
        String body = mvc.perform(post("/api/documents").with(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Contract\",\"content\":\"v1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void share(RequestPostProcessor owner, String docId, String targetUserId, Permission permission) throws Exception {
        mvc.perform(put("/api/documents/{id}/shares", docId).with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + targetUserId + "\",\"permission\":\"" + permission + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void healthIsOpenWithoutToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void apiRequiresToken() throws Exception {
        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutTenantIsForbidden() throws Exception {
        mvc.perform(get("/api/documents").with(jwt().jwt(j -> j.subject(newId())))).andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotCreate() throws Exception {
        mvc.perform(post("/api/documents").with(user(newId(), "acme", "viewer"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankTitleIsBadRequest() throws Exception {
        mvc.perform(post("/api/documents").with(user(newId(), "acme", "editor"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void strangerInSameTenantIsForbidden() throws Exception {
        var alice = user(newId(), "acme", "editor");
        String doc = createDocument(alice);

        mvc.perform(get("/api/documents/{id}", doc).with(user(newId(), "acme", "viewer")))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherTenantGetsNotFoundEvenAsAdmin() throws Exception {
        String doc = createDocument(user(newId(), "acme", "editor"));

        mvc.perform(get("/api/documents/{id}", doc).with(user(newId(), "globex", "admin")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/documents/{id}", doc).with(user(newId(), "globex", "admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantAdminCanReadAndDeleteForeignDocumentInOwnTenant() throws Exception {
        String doc = createDocument(user(newId(), "acme", "editor"));
        var admin = user(newId(), "acme", "admin");

        mvc.perform(get("/api/documents/{id}", doc).with(admin)).andExpect(status().isOk());
        mvc.perform(delete("/api/documents/{id}", doc).with(admin)).andExpect(status().isNoContent());
    }

    @Test
    void viewerWithReadShareCanReadButNotWriteOrDelete() throws Exception {
        var alice = user(newId(), "acme", "editor");
        String bobId = newId();
        var bob = user(bobId, "acme", "viewer");
        String doc = createDocument(alice);
        share(alice, doc, bobId, Permission.READ);

        mvc.perform(get("/api/documents/{id}", doc).with(bob)).andExpect(status().isOk());
        mvc.perform(put("/api/documents/{id}", doc).with(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"hack\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/documents/{id}", doc).with(bob)).andExpect(status().isForbidden());
    }

    @Test
    void editorWithWriteShareCanUpdate() throws Exception {
        var alice = user(newId(), "acme", "editor");
        String bobId = newId();
        var bob = user(bobId, "acme", "editor");
        String doc = createDocument(alice);
        share(alice, doc, bobId, Permission.WRITE);

        mvc.perform(put("/api/documents/{id}", doc).with(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Edited\",\"content\":\"v2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Edited"));
    }

    @Test
    void editorWithReadOnlyShareCannotUpdate() throws Exception {
        var alice = user(newId(), "acme", "editor");
        String bobId = newId();
        var bob = user(bobId, "acme", "editor");
        String doc = createDocument(alice);
        share(alice, doc, bobId, Permission.READ);

        mvc.perform(put("/api/documents/{id}", doc).with(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"hack\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyOwnerManagesShares() throws Exception {
        var alice = user(newId(), "acme", "editor");
        String bobId = newId();
        var bob = user(bobId, "acme", "editor");
        String doc = createDocument(alice);
        share(alice, doc, bobId, Permission.WRITE);

        mvc.perform(put("/api/documents/{id}/shares", doc).with(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + newId() + "\",\"permission\":\"READ\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/documents/{id}/shares", doc).with(bob)).andExpect(status().isForbidden());
    }

    @Test
    void listShowsOwnAndSharedDocumentsOnly() throws Exception {
        var alice = user(newId(), "acme", "editor");
        String bobId = newId();
        var bob = user(bobId, "acme", "viewer");
        String shared = createDocument(alice);
        createDocument(alice); // not shared with bob
        share(alice, shared, bobId, Permission.READ);

        mvc.perform(get("/api/documents").with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(shared));
    }

    @Test
    void sharingWithOwnerIsBadRequest() throws Exception {
        String aliceId = newId();
        var alice = user(aliceId, "acme", "editor");
        String doc = createDocument(alice);

        mvc.perform(put("/api/documents/{id}/shares", doc).with(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + aliceId + "\",\"permission\":\"READ\"}"))
                .andExpect(status().isBadRequest());
    }
}
