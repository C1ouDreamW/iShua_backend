# iShua 后端部署说明

本文档说明 iShua 后端在本地开发和服务器部署时需要准备的环境、配置项、数据库初始化、Java API 启动以及 Python AI Worker 启动方式。

## 运行组件

后端由两部分组成：

- Java API：Spring Boot 应用，提供用户、题库、刷题、错题本、AI 导入任务提交和任务状态查询等 HTTP 接口。
- Python AI Worker：独立进程，消费 Redis Stream 中的导入任务，调用 MinerU 与 LLM API，产出题目预览结果。

依赖的外部服务：

- MySQL 8.x
- Redis 6.x 或更高版本
- MinerU API
- 兼容 OpenAI SDK 的 LLM API

## 基础环境

建议版本：

| 组件 | 版本 |
| --- | --- |
| JDK | 17 |
| Maven | 3.8+ |
| MySQL | 8.x |
| Redis | 6.x+ |
| Python | 3.10+ |

后端根目录为 `backend/`。仓库根目录下也提供了 Windows 一键启动脚本：

- `Backend_st.bat`：加载 `backend/.env` 后启动 Spring Boot。
- `AiImport_st.bat`：进入 `backend/ai-import-worker`，创建/复用虚拟环境并启动 Worker。

## 数据库初始化

创建数据库：

```sql
CREATE DATABASE ishua_backend DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

初始化核心表：

```bash
cd backend
mysql -h 127.0.0.1 -P 3306 -u root -p ishua_backend < sql/schema/init_core_tables.sql
```

如果是从旧版本数据库升级，按实际缺失字段执行 `sql/schema/` 下的增量脚本：

- `ai_import_task.sql`
- `ai_import_task_add_pipeline_metrics.sql`

全新部署优先使用 `init_core_tables.sql`，该脚本已经包含用户、题库、AI 导入任务、试题和错题本表。

## Java API 配置

默认激活 profile：

```yaml
spring:
  profiles:
    active: prod
```

`application-prod.yaml` 默认监听 `8080` 端口。生产环境建议通过环境变量覆盖配置，不要把密钥写入仓库。

常用环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_HOST` | `127.0.0.1` | MySQL 地址 |
| `DB_PORT` | `3306` | MySQL 端口 |
| `DB_NAME` | `ishua_backend` | 数据库名 |
| `DB_USER` | `root` | 数据库用户名 |
| `DB_PASSWORD` | `root` | 数据库密码 |
| `REDIS_HOST` | `127.0.0.1` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `JWT_SECRET` | 示例占位值 | JWT HMAC 密钥，生产必须替换 |
| `FILE_UPLOAD_DIR` | `./data/upload` | 上传文件落盘目录，Java 与 Worker 必须都能访问 |
| `AI_IMPORT_RATE_LIMIT_PER_HOUR` | `5` | 每用户每小时 AI 导入次数 |
| `AI_IMPORT_TASK_TIMEOUT_MS` | `1800000` | AI 任务超时时间，默认 30 分钟 |
| `AI_IMPORT_CLEANUP_DEFAULT_OLDER_THAN_DAYS` | `7` | 管理端清理未确认任务的默认天数 |
| `AI_IMPORT_REDIS_SYNC_ENABLED` | `true` | 是否开启 Redis 状态同步到 MySQL |
| `AI_IMPORT_REDIS_SYNC_INTERVAL_MS` | `30000` | Redis 状态同步间隔 |

示例 `backend/.env`：

```env
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=ishua_backend
DB_USER=root
DB_PASSWORD=change-me
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=
JWT_SECRET=replace-with-a-long-random-secret-at-least-256-bits
FILE_UPLOAD_DIR=D:/C1ouD/iShua/backend/data/upload
AI_IMPORT_RATE_LIMIT_PER_HOUR=5
```

本地启动：

```bash
cd backend
mvn spring-boot:run
```

打包部署：

```bash
cd backend
mvn clean package
java -jar target/ishua-backend-0.0.1-SNAPSHOT.jar
```

启动后可访问：

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

## Python AI Worker 配置

Worker 目录：

```bash
cd backend/ai-import-worker
```

安装依赖：

```bash
python -m venv .venv
.venv/Scripts/activate
pip install -r requirements.txt
```

Linux/macOS 激活命令为：

```bash
source .venv/bin/activate
```

Worker 读取 `backend/ai-import-worker/.env` 或当前进程环境变量。常用配置：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `REDIS_URL` | `redis://localhost:6379/0` | Worker 使用的 Redis URL |
| `REDIS_STREAM` | `ishua:task:stream` | Java 写入的任务流 |
| `REDIS_GROUP` | `ishua-ai-workers` | Redis Stream 消费组 |
| `REDIS_CONSUMER` | `worker_node_1` | 当前 Worker 消费者名 |
| `REDIS_BLOCK_MS` | `5000` | 阻塞读取超时 |
| `MINERU_TOKEN` | 空 | MinerU Token，必填 |
| `MINERU_BASE_URL` | `https://mineru.net/api/v4` | MinerU API 地址 |
| `MINERU_MODEL_VERSION` | `vlm` | MinerU 模型版本 |
| `MINERU_LANGUAGE` | `ch` | 文档语言 |
| `MINERU_POLL_TIMEOUT_SECONDS` | `1800` | MinerU 轮询超时 |
| `LLM_API_KEY` | 空 | LLM API Key，`SKIP_LLM=false` 时必填 |
| `LLM_BASE_URL` | `https://api.deepseek.com` | LLM API 地址 |
| `LLM_MODEL` | `deepseek-chat` | LLM 模型 |
| `LLM_TEMPERATURE` | `0.0` | 抽题温度 |
| `LLM_SYSTEM_PROMPT_PATH` | 空 | 自定义系统提示词路径 |
| `LOG_LEVEL` | `INFO` | 日志级别 |
| `DEBUG_MODE` | `false` | 是否保存调试产物 |
| `DEBUG_TEMP_DIR` | `ai-import-worker/temp` | 调试产物目录 |
| `SKIP_LLM` | `false` | 是否跳过 LLM，仅用于联调 |

示例 `backend/ai-import-worker/.env`：

```env
REDIS_URL=redis://localhost:6379/0
REDIS_STREAM=ishua:task:stream
REDIS_GROUP=ishua-ai-workers
REDIS_CONSUMER=worker_node_1
MINERU_TOKEN=replace-me
LLM_API_KEY=replace-me
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-chat
LOG_LEVEL=INFO
```

启动 Worker：

```bash
cd backend/ai-import-worker
python main.py
```

## 文件存储要求

AI 导入会先由 Java API 将上传文件保存到 `FILE_UPLOAD_DIR`，再把 `file://...` 路径写入 Redis Stream。Worker 会按该路径读取文件。

因此部署时必须保证：

- Java API 和 Worker 在同一台机器，或共享同一个文件系统目录。
- `FILE_UPLOAD_DIR` 使用绝对路径更稳妥。
- 运行 Java API 和 Worker 的用户都具有读写该目录的权限。

## Redis Key 与 Stream

关键 Redis 资源：

| 名称 | 用途 |
| --- | --- |
| `ishua:task:stream` | AI 导入任务流 |
| `ishua-ai-workers` | Worker 消费组 |
| `ishua:task:meta:{taskId}` | 任务元数据 |
| `ishua:task:status:{taskId}` | 任务状态 |
| `ishua:task:result:{taskId}` | 预览题目结果 |
| `ishua:task:import_lock:{taskId}` | 批量入库幂等锁 |
| `smart_ishua:bank_detail:{bankId}` | 公开题库热点缓存 |

## 部署检查清单

- MySQL 已创建 `ishua_backend` 并执行初始化 SQL。
- Redis 可被 Java API 和 Worker 访问。
- `JWT_SECRET` 已替换为足够长的随机密钥。
- `FILE_UPLOAD_DIR` 为 Java API 和 Worker 共享可访问目录。
- Java API 已启动，`/swagger-ui.html` 可访问。
- Worker 已启动，日志中无 `MINERU_TOKEN is required` 或 `LLM_API_KEY is required`。
- PREMIUM 或 ADMIN 用户可提交 AI 导入任务。
