
-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 为文档切片添加 1024 维向量字段
ALTER TABLE document_chunks
    ADD COLUMN embedding vector(1024);

-- 创建 HNSW 向量索引，使用余弦距离进行相似度检索
CREATE INDEX idx_document_chunks_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops);
