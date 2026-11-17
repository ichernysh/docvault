package com.docvault.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DocumentPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired DocumentRepository documents;
    @Autowired DocumentShareRepository shares;
    @Autowired EntityManager em;

    @Test
    void foreignTenantCannotSeeDocument() {
        var doc = documents.saveAndFlush(new Document("t1", "u1", "Spec", "body"));

        assertThat(documents.findByIdAndTenantId(doc.getId(), "t1")).isPresent();
        assertThat(documents.findByIdAndTenantId(doc.getId(), "t2")).isEmpty();
    }

    @Test
    void sharesAreDeletedWithDocument() {
        var doc = documents.saveAndFlush(new Document("t1", "u1", "Spec", "body"));
        shares.saveAndFlush(new DocumentShare(doc.getId(), "u2", Permission.READ));
        assertThat(shares.findByIdDocumentId(doc.getId())).hasSize(1);

        documents.deleteById(doc.getId());
        documents.flush();
        em.clear();

        assertThat(shares.findByIdDocumentId(doc.getId())).isEmpty();
    }
}
