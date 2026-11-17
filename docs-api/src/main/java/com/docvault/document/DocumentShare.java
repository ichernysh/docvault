package com.docvault.document;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_shares")
public class DocumentShare {

    @EmbeddedId
    private DocumentShareId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Permission permission;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentShare() {
    }

    public DocumentShare(UUID documentId, String userId, Permission permission) {
        this.id = new DocumentShareId(documentId, userId);
        this.permission = permission;
        this.createdAt = Instant.now();
    }

    public void changePermission(Permission permission) {
        this.permission = permission;
    }

    public DocumentShareId getId() { return id; }
    public Permission getPermission() { return permission; }
    public Instant getCreatedAt() { return createdAt; }
}
