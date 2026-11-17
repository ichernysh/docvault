package com.docvault.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentShareRepository extends JpaRepository<DocumentShare, DocumentShareId> {

    List<DocumentShare> findByIdDocumentId(UUID documentId);
}
