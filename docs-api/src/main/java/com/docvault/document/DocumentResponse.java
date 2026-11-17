package com.docvault.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id, String title, String content, String ownerId, Instant createdAt, Instant updatedAt) {

    static DocumentResponse from(Document d) {
        return new DocumentResponse(
                d.getId(), d.getTitle(), d.getContent(), d.getOwnerId(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
