# RAG 项目数据模型设计

## 1. 设计目标

定义文档、文本片段和文档状态三个核心模型，为后续文档上传、解析、切片、索引和检索功能提供统一的数据结构。

本设计与 Day 5 已创建的 PostgreSQL 表结构保持一致。

## 2. Document 文档模型

### 2.1 字段定义

| 字段 | Java 类型 | 数据库字段 | 说明 |
| --- | --- | --- | --- |
| id | UUID | id | 文档唯一标识 |
| originalFilename | String | original_filename | 原始文件名，最长 255 字符 |
| contentHash | String | content_hash | 文件内容哈希，长度 64 |
| status | DocumentStatus | status | 文档处理状态 |
| createdAt | OffsetDateTime | created_at | 创建时间 |
| updatedAt | OffsetDateTime | updated_at | 最后更新时间 |

### 2.2 设计约束

- id 使用 UUID，避免依赖数据库自增 ID。
- contentHash 用于识别内容相同的文件。
- status 只能使用 DocumentStatus 定义的合法状态。
- 创建时间和更新时间使用带时区的时间类型。
- 文档状态变化时，应同步更新 updatedAt。

## 3. Chunk 文本片段模型

### 3.1 字段定义

| 字段 | Java 类型 | 数据库字段 | 说明 |
| --- | --- | --- | --- |
| id | UUID | id | 文本片段唯一标识 |
| documentId | UUID | document_id | 所属文档 ID |
| chunkIndex | Integer | chunk_index | 片段在文档中的顺序，从 0 开始 |
| content | String | content | 片段文本内容 |
| pageNumber | Integer | page_number | 原文页码，可为空 |
| sectionTitle | String | section_title | 原文章节标题，可为空 |
| createdAt | OffsetDateTime | created_at | 创建时间 |

### 3.2 设计约束

- 一个 Document 可以对应多个 Chunk。
- 每个 Chunk 必须关联一个已存在的 Document。
- chunkIndex 从 0 开始，且不能为负数。
- 同一文档内的 chunkIndex 不能重复。
- content 不能为空。
- pageNumber 和 sectionTitle 用于保留原文位置信息。
- 删除文档时，其关联的 Chunk 由数据库外键级联删除。

## 4. DocumentStatus 文档状态

定义四种状态：

| 状态 | 含义 |
| --- | --- |
| UPLOADED | 文档已上传并登记 |
| PROCESSING | 文档正在解析和索引 |
| READY | 文档处理完成，可以参与检索 |
| FAILED | 文档处理失败 |

### 4.1 状态流转

```text
UPLOADED
    |
    v
PROCESSING
    |
    +------> READY
    |
    +------> FAILED
```

### 4.2 状态约束

- 文档登记成功后，初始状态为 UPLOADED。
- 开始解析和索引时，状态变为 PROCESSING。
- 解析、切片及索引流程成功完成后，状态变为 READY。
- 处理过程中发生不可恢复的错误时，状态变为 FAILED。
- 状态流转必须由服务端控制，不能直接信任客户端传入的状态。
- FAILED 文档的重试规则在后续异步处理阶段进一步设计。

## 5. 模型与数据库的对应关系

| 领域模型 | 数据库表 |
| --- | --- |
| Document | documents |
| Chunk | document_chunks |
| DocumentStatus | documents.status 字段使用的状态值 |

Flyway 的 flyway_schema_history 表用于记录数据库迁移历史，不属于本项目的业务领域模型。

## 6. 核心关系

一个文档包含零个或多个文本片段。

每个文本片段只属于一个文档。

```text
Document 1 -------- N Chunk
```

DocumentStatus 用于描述 Document 当前的处理状态，不单独建立数据库表。

## 7. 当前阶段的实现边界

Day 6 只完成数据模型设计，不在本阶段创建 Java 实体类，也不修改已经执行过的 V1__init.sql。

后续实现应遵循本文档，并以实际数据库迁移文件为准。

如果后续确实需要新增数据库字段，应通过新的 Flyway 迁移文件完成。