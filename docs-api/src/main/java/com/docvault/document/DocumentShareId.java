package com.docvault.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public record DocumentShareId(
        @Column(name = "document_id") UUID documentId,
        @Column(name = "user_id") String userId) {
}
