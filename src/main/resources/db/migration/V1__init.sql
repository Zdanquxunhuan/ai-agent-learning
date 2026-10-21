-- 文档表
CREATE TABLE documents (
    id UUID PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_documents_status
        CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED'))
);

-- 文档切片表
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    page_number INTEGER,
    section_title VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_document_chunks_document
        FOREIGN KEY (document_id)
        REFERENCES documents(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_document_chunks_document_index
        UNIQUE (document_id, chunk_index),

    CONSTRAINT chk_document_chunks_index
        CHECK (chunk_index >= 0)
);

-- 为内容哈希建立索引
CREATE INDEX idx_documents_content_hash
    ON documents(content_hash);