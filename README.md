
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
