CREATE TABLE documents (
    id         UUID PRIMARY KEY,
    tenant_id  VARCHAR(64)  NOT NULL,
    owner_id   VARCHAR(64)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    content    TEXT         NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

-- every query is tenant-scoped, so tenant_id leads the indexes
CREATE INDEX idx_documents_tenant ON documents (tenant_id, created_at DESC);
CREATE INDEX idx_documents_owner ON documents (tenant_id, owner_id);

CREATE TABLE document_shares (
    document_id UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    user_id     VARCHAR(64) NOT NULL,
    permission  VARCHAR(16) NOT NULL CHECK (permission IN ('READ', 'WRITE')),
    created_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (document_id, user_id)
);

-- "documents shared with me"
CREATE INDEX idx_shares_user ON document_shares (user_id);
