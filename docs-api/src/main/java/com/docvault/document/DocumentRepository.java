package com.docvault.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    // tenant scoping lives in the query: a foreign-tenant id behaves as "not found"
    Optional<Document> findByIdAndTenantId(UUID id, String tenantId);

    /** Documents the user owns or that were shared with them, inside one tenant. */
    @Query("""
            select d from Document d
            where d.tenantId = :tenantId
              and (d.ownerId = :userId
                   or exists (select 1 from DocumentShare s
                              where s.id.documentId = d.id and s.id.userId = :userId))
            order by d.createdAt desc
            """)
    List<Document> findVisibleTo(@Param("tenantId") String tenantId, @Param("userId") String userId);
}
