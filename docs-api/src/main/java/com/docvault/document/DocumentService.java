package com.docvault.document;

import com.docvault.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped business logic. Authorization (who may do what) is NOT decided here:
 * it is declared with {@code @PreAuthorize} on the controller via {@link DocumentAccess}.
 */
@Service
@Transactional
public class DocumentService {

    private final DocumentRepository documents;
    private final DocumentShareRepository shares;

    public DocumentService(DocumentRepository documents, DocumentShareRepository shares) {
        this.documents = documents;
        this.shares = shares;
    }

    public DocumentResponse create(CurrentUser user, DocumentRequest request) {
        var doc = new Document(user.tenantId(), user.userId(), request.title(), contentOrEmpty(request));
        return DocumentResponse.from(documents.save(doc));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(CurrentUser user) {
        return documents.findVisibleTo(user.tenantId(), user.userId()).stream()
                .map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(CurrentUser user, UUID id) {
        return DocumentResponse.from(find(user, id));
    }

    public DocumentResponse update(CurrentUser user, UUID id, DocumentRequest request) {
        var doc = find(user, id);
        doc.update(request.title(), contentOrEmpty(request));
        return DocumentResponse.from(doc);
    }

    public void delete(CurrentUser user, UUID id) {
        documents.delete(find(user, id)); // shares go away via ON DELETE CASCADE
    }

    public ShareResponse share(CurrentUser user, UUID id, ShareRequest request) {
        var doc = find(user, id);
        if (doc.getOwnerId().equals(request.userId())) {
            throw new IllegalArgumentException("Cannot share a document with its owner");
        }
        var shareId = new DocumentShareId(id, request.userId());
        var share = shares.findById(shareId).orElseGet(() -> new DocumentShare(id, request.userId(), request.permission()));
        share.changePermission(request.permission());
        return ShareResponse.from(shares.save(share));
    }

    public void unshare(CurrentUser user, UUID id, String targetUserId) {
        find(user, id);
        shares.deleteById(new DocumentShareId(id, targetUserId));
    }

    @Transactional(readOnly = true)
    public List<ShareResponse> listShares(CurrentUser user, UUID id) {
        find(user, id);
        return shares.findByIdDocumentId(id).stream().map(ShareResponse::from).toList();
    }

    private Document find(CurrentUser user, UUID id) {
        return documents.findByIdAndTenantId(id, user.tenantId()).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private static String contentOrEmpty(DocumentRequest request) {
        return request.content() == null ? "" : request.content();
    }
}
