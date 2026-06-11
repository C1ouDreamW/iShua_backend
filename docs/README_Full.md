# Atlas - 智能在线题库与刷题平台 (iShua Backend)

![Java](https://img.shields.io/badge/Java-17-blue.svg)
![Python](https://img.shields.io/badge/Python-3.10+-blue.svg)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.5.x-brightgreen.svg)
![MySQL](https://img.shields.io/badge/MySQL-8.x-orange.svg)
![Redis](https://img.shields.io/badge/Redis-Cache-red.svg)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5-yellow.svg)

🌐 在线 Demo：https://ishua.heycloudream.cn

Atlas 是一款专为大学生考试备考痛点设计的智能在线刷题平台后端服务。本项目旨在提供高效的习题录入、智能化试题解析、高并发访问下的热点题库管理以及个性化错题本追踪等核心功能，打造一体化、智能通用的试题与学习管理解决方案。

## 核心特性 (Key Features)

- **安全可靠的用户与鉴权系统**：基于 JWT 和全无状态的令牌认证体系，结合 BCrypt 强哈希算法保护密码安全。支持角色权限隔离。
- **多模态 AI 智能导题引擎**：支持 `.docx`、`.pdf`、`.txt` 上传；Java 作为生产者入队 Redis Stream，**Python Worker**（`ai-import-worker/`）通过 MinerU 解析文档并调用大模型（DeepSeek 等），经预览确认后幂等落库。
- **完善的题库管理生态**：对海量试题与聚合题库提供完整的 CRUD 生命周期管理，支持公开热点题库分享机制。
- **高并发缓存调优**：创新性结合 Redis，深入处理热点题库数据的共享读取（Cache-Aside），应对高并发查询挑战，缓解 MySQL 数据库峰值加载压力。
- **在线刷题与错题本闭环**：支持按题库顺序/随机刷题、服务端自动判分、客观题答错自动归档、错题分页查看/移除/重刷，提升复习与巩固效率。

## 系统架构与技术栈 (Architecture & Tech Stack)

- **核心语言**：Java 17
- **基础框架**：Spring Boot 3.x
- **持久层封装平台**：MyBatis-Plus 3.5.x
- **数据库存储**：MySQL 8.x
- **高速缓存中心**：Redis
- **安全与校验**：JSON Web Token (JWT) / Spring Validation
- **文档与调试工具**：SpringDoc OpenAPI 2.7.0 (Swagger UI)
- **依赖与构建**：Maven

## 前置要求 (Prerequisites)

为了成功搭建和运行本项目，你需要提前在本地或服务器环境中准备：

- **JDK**: >= 17 （建议配置好 `JAVA_HOME`）
- **Maven**: >= 3.8.x
- **MySQL**: >= 8.0（开启 utf8mb4 支持）
- **Redis**: 稳定运行版本即可（AI 导入 Stream + 缓存 + 限流均依赖）
- **Python**: >= 3.10（运行 `ai-import-worker` Worker；仅本地刷题/题库 CRUD 可不启）
- **IDE 推荐**: IntelliJ IDEA，需安装并启用 Lombok 插件。

### 核心环境变量规划

**Java 后端**（见 `src/main/resources/application-dev.yaml`，支持环境变量覆盖）：

| 变量 | 说明 |
|------|------|
| `DB_*` | MySQL 连接 |
| `REDIS_*` | Redis 连接 |
| `JWT_SECRET` | JWT 签名密钥 |
| `FILE_UPLOAD_DIR` | AI 导入文件落盘目录（须与 Python Worker 共享） |
| `AI_IMPORT_RATE_LIMIT_PER_HOUR` | 每用户 AI 导入提交上限（默认 5） |
| `AI_IMPORT_TASK_TIMEOUT_MS` | Watchdog 超时阈值（默认 1800000 ms） |

**Python Worker**（复制 `ai-import-worker/.env.example` 为 `.env`）：

| 变量 | 说明 |
|------|------|
| `REDIS_URL` / `REDIS_STREAM` / `REDIS_GROUP` | 与 Java 侧 Stream 一致 |
| `MINERU_TOKEN` | MinerU 文档解析 API |
| `LLM_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL` | 大模型（默认 DeepSeek） |

## 安装与启动逻辑 (Getting Started)

### 1. 数据库初始化
进入 MySQL，执行仓库中提供的 `sql/schema/init_core_tables.sql` 初始化数据表：
```bash
mysql -u root -p < sql/schema/init_core_tables.sql
```

### 2. 构建与运行 Java 后端
克隆项目后，可以使用 Maven 在本地迅速编译并启动：
```bash
# 进入项目目录
cd backend

# 下载第三方依赖与刷新
mvn clean install -DskipTests

# 本地启动 Spring Boot 应用程序
mvn spring-boot:run
```

或通过 `java -jar` 运行构建产物：
```bash
mvn package -DskipTests
java -jar target/ishua-backend-0.0.1-SNAPSHOT.jar
```

### 3. 启动 AI 导入 Worker（可选）

使用 AI 智能导题时需另启 Python 进程：

```bash
cd ai-import-worker
python -m venv .venv
# Windows: .venv\Scripts\activate  |  Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # 填入 REDIS_URL、MINERU_TOKEN、LLM_API_KEY
python main.py
```

Java 与 Worker 须访问**同一 Redis** 与**同一文件落盘目录**（`FILE_UPLOAD_DIR` / Worker 读取 Stream 中的绝对路径）。

## 调试与开发指南 (Debugging & Development)

### 调试规范
1. 均采用 MVC 标准的三层架构推进流：`Controller` -> `Service` -> `Mapper` -> `Entity`。
2. 数据流转规范采用完整的实体切分策略：接收使用 `DTO`，返回前端装配 `VO`，严禁将数据层 `Entity` 直接通过 API 暴露。
3. 异常处理：在 `GlobalExceptionHandler` 捕获异常，绝不在业务代码中过度散落 `try/catch`，推荐显式抛出 `BusinessException` 等自定义异常。

### 单元测试集成
项目中集成了强大的 H2 内存数据库以及单元测试模块：
```bash
# 运行单元测试
mvn test
```
测试报告和异常堆栈会输出至 `target/surefire-reports` 中。

## 核心 API 接口概览 (API Reference)

*完整的交互契约与请求示例，可通过启动应用并访问 Swagger UI 获得 (通常位于 `http://localhost:8080/swagger-ui.html`)*。以下为项目核心 RESTful 范式的 API 总览：

| 功能模块 | HTTP | 路由路径 | 鉴权 | 说明 |
| --- | --- | --- | --- | --- |
| **用户鉴权** | `POST` | `/api/v1/users/register` | 否 | 用户注册 |
| **用户鉴权** | `POST` | `/api/v1/users/login` | 否 | 登录，返回 JWT |
| **题库树** | `GET` | `/api/v1/bank-nodes/public/roots` | 公开 | 根节点分页（大厅入口） |
| **题库树** | `GET` | `/api/v1/bank-nodes/public/tree` | 公开 | 公开扁平树列表 |
| **题库树** | `GET` | `/api/v1/bank-nodes/mine/roots` | `PREMIUM+` | 当前用户根节点分页 |
| **题库树** | `GET` | `/api/v1/bank-nodes/mine/tree` | `PREMIUM+` | 当前用户扁平树列表 |
| **题库树** | `POST` | `/api/v1/bank-nodes` | JWT | 创建 FOLDER/LEAF |
| **题库管理** | `GET` | `/api/v1/question-banks` | JWT | 我的 LEAF 题库分页（兼容） |
| **题库大厅** | `GET` | `/api/v1/question-banks/public` | 否 | 公开 LEAF 分页（兼容） |
| **题库管理** | `POST` | `/api/v1/question-banks` | JWT | 创建题库 |
| **题库管理** | `PUT` | `/api/v1/question-banks/{bankId}` | JWT | 更新题库 |
| **题库管理** | `DELETE` | `/api/v1/question-banks/{bankId}` | JWT | 删除题库 |
| **刷题聚合** | `GET` | `/api/v1/question-banks/{bankId}/hot-practice-detail` | 否 | 公开题库全量试题（Redis） |
| **试题管理** | `GET` | `/api/v1/question-banks/{bankId}/questions` | JWT | 题库下试题分页（可选 `keyword`） |
| **试题管理** | `POST` | `/api/v1/question-banks/{bankId}/questions` | JWT | 新增试题 |
| **试题管理** | `GET` | `/api/v1/questions/{id}` | JWT | 试题详情 |
| **试题管理** | `PUT` | `/api/v1/questions/{id}` | JWT | 更新试题 |
| **试题管理** | `DELETE` | `/api/v1/questions/{id}` | JWT | 删除试题 |
| **智能导题** | `POST` | `/api/v1/ai-import/submit` | JWT | 上传文件异步导题（multipart） |
| **智能导题** | `GET` | `/api/v1/ai-import/tasks/{taskId}/status` | JWT | 轮询任务与预览 |
| **智能导题** | `POST` | `/api/v1/question-banks/{bankId}/questions/batch` | JWT | 确认导入（幂等） |
| **在线刷题** | `GET` | `/api/v1/practice/banks/{bankId}/questions` | JWT | 获取刷题题目列表（不含答案/解析，可随机） |
| **在线刷题** | `POST` | `/api/v1/practice/banks/{bankId}/questions/{questionId}/submit` | JWT | 提交单题答案并返回判分结果 |
| **错题本** | `GET` | `/api/v1/wrong-questions` | JWT | 分页查看当前用户错题（可按 `bankId` 过滤） |
| **错题本** | `DELETE` | `/api/v1/wrong-questions/{id}` | JWT | 移除本人错题记录（逻辑删除） |
| **错题本** | `GET` | `/api/v1/wrong-questions/practice` | JWT | 按错题本获取重刷题目列表（不含答案/解析） |

> 除标注「否」外，请求头须 `Authorization: Bearer <token>`。统一响应 `Result<T>`（`code`/`message`/`data`）；业务失败时 **HTTP 仍为 200**，以 `code` 区分。分页 `data` 为 `PageResultVO`（`total` + `records`）。**完整 Schema 与 Try it out 见 Swagger UI。**

刷题模式下，题目列表只返回题干、题型、选项和排序信息，答案与解析仅在提交答案后返回。公开题库刷题读取复用 Redis 热点题库缓存；私有题库需要通过当前登录用户的归属校验。客观题答错会自动进入错题本，主观题或未知题型会标记为需人工判分。

## 核心项目目录结构 (Project Structure)

```text
├── sql/
│   ├── schema/              # 核心表 DDL
│   └── data/                # 种子数据、角色迁移脚本等
├── ai-import-worker/           # AI 导入 Worker（MinerU + LLM，消费 Redis Stream）
├── src/
│   ├── main/
│   │   ├── java/cn/.../ishua_backend/
│   │   │   ├── common/      # 公共常量、分页 DTO、Result/PageResultVO
│   │   │   ├── config/      # JWT/角色拦截、MyBatis-Plus、OpenAPI、CORS
│   │   │   ├── controller/  # REST 控制器（含 admin/ 管理端）
│   │   │   ├── dto/         # 请求 DTO
│   │   │   ├── entity/      # MyBatis-Plus 实体
│   │   │   ├── enums/       # 业务枚举
│   │   │   ├── exception/   # BusinessException + 全局处理
│   │   │   ├── mapper/      # Mapper 接口
│   │   │   ├── service/
│   │   │   │   ├── ai/      # Stream 派发、任务状态/结果、限流、Watchdog
│   │   │   │   ├── file/    # 上传文件落盘
│   │   │   │   ├── guard/   # BankAccessGuard 归属校验
│   │   │   │   └── impl/    # Service 实现
│   │   │   ├── util/        # JWT、UserContextHolder 等
│   │   │   └── vo/          # 响应 VO
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── application-dev.yaml
│   └── test/                # 单元与集成测试
└── pom.xml
```

## 贡献规范与协议 (Contributing & License)

所有 PRs 都应满足以下工程标准：
- 新需求代码应该使用驼峰命名法（`camelCase` / `PascalCase`）；
- 中文注释，且方法名具有高度可读性；
- 耗时第三方调用（MinerU、LLM 等）放在 **`ai-import-worker` Worker** 或独立进程中，Java 侧通过 Redis Stream / 状态键协作，避免在 HTTP 请求线程内阻塞。