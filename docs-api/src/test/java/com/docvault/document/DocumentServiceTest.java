package com.docvault.document;

import com.docvault.PostgresTestConfig;
import com.docvault.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, DocumentService.class, DocumentAccess.class})
class DocumentServiceTest {

    @Autowired DocumentService service;
    @Autowired DocumentAccess access;

    private static final CurrentUser ALICE = new CurrentUser("alice", "acme");
    private static final CurrentUser BOB = new CurrentUser("bob", "acme");
    private static final CurrentUser MALLORY = new CurrentUser("mallory", "evil");

    private static Authentication auth(CurrentUser u, String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
                .subject(u.userId()).claim(CurrentUser.TENANT_CLAIM, u.tenantId()).build();
        return new JwtAuthenticationToken(jwt,
                java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private UUID aliceDoc() {
        return service.create(ALICE, new DocumentRequest("Contract", "text")).id();
    }

    @Test
    void ownerSeesOwnDocumentInList() {
        UUID id = aliceDoc();

        assertThat(service.list(ALICE)).extracting(DocumentResponse::id).contains(id);
        assertThat(service.list(BOB)).extracting(DocumentResponse::id).doesNotContain(id);
    }

    @Test
    void foreignTenantGetsNotFound() {
        UUID id = aliceDoc();

        assertThatThrownBy(() -> service.get(MALLORY, id)).isInstanceOf(DocumentNotFoundException.class);
        assertThatThrownBy(() -> access.canRead(id, auth(MALLORY, "ADMIN")))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void sharedDocumentAppearsInListAndGrantsReadOnly() {
        UUID id = aliceDoc();
        service.share(ALICE, id, new ShareRequest("bob", Permission.READ));

        assertThat(service.list(BOB)).extracting(DocumentResponse::id).contains(id);
        assertThat(access.canRead(id, auth(BOB, "VIEWER"))).isTrue();
        assertThat(access.canWrite(id, auth(BOB, "VIEWER"))).isFalse();
        assertThat(access.isOwner(id, auth(BOB, "VIEWER"))).isFalse();
    }

    @Test
    void resharingUpgradesPermissionToWrite() {
        UUID id = aliceDoc();
        service.share(ALICE, id, new ShareRequest("bob", Permission.READ));
        service.share(ALICE, id, new ShareRequest("bob", Permission.WRITE));

        assertThat(service.listShares(ALICE, id)).containsExactly(new ShareResponse("bob", Permission.WRITE));
        assertThat(access.canWrite(id, auth(BOB, "EDITOR"))).isTrue();
    }

    @Test
    void unshareRevokesAccess() {
        UUID id = aliceDoc();
        service.share(ALICE, id, new ShareRequest("bob", Permission.READ));
        service.unshare(ALICE, id, "bob");

        assertThat(access.canRead(id, auth(BOB, "VIEWER"))).isFalse();
    }

    @Test
    void strangerInSameTenantHasNoAccessButAdminDoes() {
        UUID id = aliceDoc();

        assertThat(access.canRead(id, auth(BOB, "VIEWER"))).isFalse();
        assertThat(access.canWrite(id, auth(BOB, "ADMIN"))).isTrue();
        assertThat(access.isOwner(id, auth(BOB, "ADMIN"))).isTrue();
    }

    @Test
    void cannotShareWithOwner() {
        UUID id = aliceDoc();

        assertThatThrownBy(() -> service.share(ALICE, id, new ShareRequest("alice", Permission.READ)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateChangesTitleAndContent() {
        UUID id = aliceDoc();

        var updated = service.update(ALICE, id, new DocumentRequest("New", null));

        assertThat(updated.title()).isEqualTo("New");
        assertThat(updated.content()).isEmpty();
    }

    @Test
    void tokenWithoutTenantClaimIsRejected() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("x").build();

        assertThatThrownBy(() -> CurrentUser.from(jwt)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteRemovesDocumentAndShares() {
        UUID id = aliceDoc();
        service.share(ALICE, id, new ShareRequest("bob", Permission.READ));

        service.delete(ALICE, id);

        assertThat(service.list(ALICE)).extracting(DocumentResponse::id).doesNotContain(id);
        assertThatThrownBy(() -> service.get(ALICE, id)).isInstanceOf(DocumentNotFoundException.class);
    }
}
