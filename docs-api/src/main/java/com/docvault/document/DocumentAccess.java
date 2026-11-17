package com.docvault.document;

import com.docvault.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Resource-level (fine-grained) checks, meant to be called from {@code @PreAuthorize}, e.g.
 * {@code @PreAuthorize("@documentAccess.canRead(#id, authentication)")}.
 *
 * <p>Semantics: a document outside the caller's tenant (or missing) -> {@link DocumentNotFoundException}
 * (404, existence is not disclosed); a visible document without enough rights -> {@code false} (403).
 * Tenant admins ({@code ROLE_ADMIN}) can do everything inside their tenant. Contract for the JWT converter:
 * realm roles ({@code admin}) must be mapped to upper-case authorities ({@code ROLE_ADMIN}); otherwise the
 * admin bypass silently never fires (fails closed).
 */
@Component("documentAccess")
public class DocumentAccess {

    private final DocumentRepository documents;
    private final DocumentShareRepository shares;

    public DocumentAccess(DocumentRepository documents, DocumentShareRepository shares) {
        this.documents = documents;
        this.shares = shares;
    }

    @Transactional(readOnly = true)
    public boolean canRead(UUID documentId, Authentication auth) {
        var ctx = load(documentId, auth);
        return ctx.admin() || ctx.owner() || ctx.share().isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canWrite(UUID documentId, Authentication auth) {
        var ctx = load(documentId, auth);
        return ctx.admin() || ctx.owner() || ctx.share().map(s -> s.getPermission().allowsWrite()).orElse(false);
    }

    /** Deleting a document and managing its shares is reserved to the owner (and tenant admins). */
    @Transactional(readOnly = true)
    public boolean isOwner(UUID documentId, Authentication auth) {
        var ctx = load(documentId, auth);
        return ctx.admin() || ctx.owner();
    }

    private record Context(boolean admin, boolean owner, Optional<DocumentShare> share) {
    }

    private Context load(UUID documentId, Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        Document doc = documents.findByIdAndTenantId(documentId, user.tenantId())
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean owner = doc.getOwnerId().equals(user.userId());
        return new Context(admin, owner, shares.findById(new DocumentShareId(documentId, user.userId())));
    }
}
