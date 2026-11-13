package com.docvault.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    // tenant scoping lives in the query: a foreign-tenant id behaves as "not found"
    Optional<Document> findByIdAndTenantId(UUID id, String tenantId);
}
