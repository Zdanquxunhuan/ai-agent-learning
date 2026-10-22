
## 本地开发配置

### 环境要求

- JDK 17
- Maven Wrapper
- Spring Boot 3.5.16

### 配置步骤

1. 复制 `.env.example` 为 `.env`。
2. 根据本地环境修改端口、模型配置和数据库配置。
3. `.env` 用于保存本地配置，不允许提交到 Git。
4. `.env.example` 只保留占位值，可以提交到 Git。
5. 模型尚未接入时，将 `RAG_MODEL_ENABLED` 设置为 `false`。
6. 启用模型后，必须填写有效的模型 URL、模型名称和 API Key。

[//]: # (配置解释:application.yml 放项目通用配置，.env 放本地环境配置和敏感信息，.env.example 是给其他开发者使用的配置模板。)

### 运行测试

在项目根目录执行：

```powershell
.\mvnw.cmd test
```

如果当前终端没有使用 JDK 17，先设置本次 PowerShell 会话：

```powershell
$env:JAVA_HOME = "F:\ayd\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

### 启动服务

```powershell
.\mvnw.cmd spring-boot:run
```

健康检查地址：

http://localhost:8080/api/v1/health

### 安全注意事项

- 不要将真实 API Key、数据库密码提交到 Git。
- 不要在日志中输出完整密钥。
- 不要将完整敏感文档写入异常日志。
- 如果真实密钥曾经提交到 Git，应立即轮换该密钥。



## PostgreSQL 数据库

### 启动数据库

```powershell
docker compose up -d postgres
```

### 检查数据库状态

```powershell
docker compose ps
```

### 查看数据库日志

```powershell
docker compose logs -f postgres
```

### 连接数据库

```powershell
docker compose exec postgres psql -U rag -d rag
```

### 查看数据库表

```powershell
docker compose exec postgres psql -U rag -d rag -c "\dt"
```

### 查看 Flyway 迁移记录

```powershell
docker compose exec postgres psql -U rag -d rag -c "SELECT version, description, success FROM flyway_schema_history;"
```

### 启动 Spring Boot 应用

```powershell
.\mvnw.cmd spring-boot:run
```

### 重启数据库

```powershell
docker compose restart postgres
```

### 停止数据库

```powershell
docker compose stop postgres
```


## 文档解析支持范围

### TXT

- 支持 UTF-8 文本解析。
- 非法 UTF-8 输入会返回解析异常。

### PDF

- 使用 Apache PDFBox 提取文本。
- 尽可能保留页码信息。
- 损坏或加密且未提供密码的 PDF 暂不支持。
- 暂不支持 OCR。

### DOCX

- 使用 Apache POI 提取段落文本。
- 尽可能保留标题和段落所属章节信息。
- 损坏或加密的 DOCX 暂不支持。
- 当前不计算 DOCX 实际页码。

### 已知限制

1. 扫描版 PDF 如果没有可提取的文本，可能得到空文本。
2. 当前没有集成 OCR 引擎，不能保证识别图片中的文字。
3. DOCX 当前主要提取段落，不保证覆盖表格、图片中的文字等所有内容。
4. 当前仅支持常规 TXT、PDF 和 DOCX 解析，不承诺兼容所有特殊格式。


