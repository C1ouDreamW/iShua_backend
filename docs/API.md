# iShua 后端 API 文档

本文档提供后端接口索引、鉴权规则、统一响应结构和常用请求体说明。字段级细节以运行时 Swagger 为准：

- Swagger UI：`/swagger-ui.html`
- OpenAPI JSON：`/v3/api-docs`

默认本地地址示例：`http://localhost:8080/swagger-ui.html`

## 通用约定

所有业务接口返回统一结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

说明：

- `code=200` 表示业务成功。
- 常见业务错误码：`400` 参数错误、`401` 未登录或 Token 无效、`403` 无权限、`404` 资源不存在、`409` 并发冲突、`429` 限流、`500` 服务端异常。
- HTTP 状态码通常仍为 200，前端应以响应体中的 `code` 判断业务结果。

分页响应结构：

```json
{
  "total": 100,
  "records": []
}
```

通用分页查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `current` | integer | 是 | 当前页，从 1 开始 |
| `pageSize` | integer | 是 | 每页条数，上限由全局校验控制 |

## 鉴权与角色

登录成功后，后续请求携带：

```http
Authorization: Bearer <token>
```

角色：

| 角色 | 说明 |
| --- | --- |
| `USER` | 普通用户，可刷公开题库、提交答案、使用错题本 |
| `PREMIUM` | 高级用户，可管理自己的题库和使用 AI 导入 |
| `ADMIN` | 管理员，可访问管理端接口，并可绕过部分资源归属校验 |

无需登录的公开接口：

- `POST /api/v1/users/register`
- `POST /api/v1/users/login`
- `GET /api/v1/question-banks/public`（兼容，返回 LEAF 节点）
- `GET /api/v1/bank-nodes/roots?scope=public`
- `GET /api/v1/bank-nodes/tree?scope=public`
- `GET /api/v1/question-banks/{bankId}/hot-practice-detail`
- `GET /api/v1/bank-nodes/{nodeId}/hot-practice-detail`
- Swagger/OpenAPI 文档路径

## 用户鉴权

Base path：`/api/v1/users`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/register` | 公开 | 用户注册 |
| `POST` | `/login` | 公开 | 用户登录，返回 JWT 与用户信息 |
| `GET` | `/me` | `USER+` | 获取当前登录用户信息 |

注册请求体：

```json
{
  "username": "zhangsan",
  "password": "123456",
  "nickname": "张三"
}
```

登录请求体：

```json
{
  "username": "zhangsan",
  "password": "123456"
}
```

登录成功后 `data` 包含 `token`、用户 ID、用户名、昵称和角色。

## 题库管理

Base path：`/api/v1/question-banks`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/` | `PREMIUM+` | 分页查询当前用户创建的题库 |
| `GET` | `/public` | 公开 | 分页查询公开题库 |
| `POST` | `/` | `PREMIUM+` | 创建题库 |
| `GET` | `/{bankId}/hot-practice-detail` | 公开 | 获取公开热点题库聚合数据，走 Redis 缓存 |
| `GET` | `/{bankId}/questions` | `PREMIUM+` | 分页查询指定题库下的试题 |
| `POST` | `/{bankId}/questions` | `PREMIUM+` | 在指定题库下新增试题 |
| `POST` | `/{bankId}/questions/batch` | `PREMIUM+` | AI 解析预览确认后批量入库 |
| `PUT` | `/{bankId}` | `PREMIUM+` | 全量更新题库 |
| `DELETE` | `/{bankId}` | `PREMIUM+` | 逻辑删除题库及其试题 |

创建/更新题库请求体：

```json
{
  "title": "2026计算机网络期末必刷题",
  "description": "面向期末周的重点题型整理",
  "isPublic": 1
}
```

说明：

- `isPublic=1` 表示公开题库，可出现在公开大厅。
- 我的题库、题库写操作和私有题库试题管理要求 `PREMIUM` 或 `ADMIN`。
- `ADMIN` 可绕过题库归属校验。

## 题库树（Phase 1）

Base path：`/api/v1/bank-nodes`

节点类型：

| `nodeKind` | 说明 |
| --- | --- |
| `FOLDER` | 文件夹容器，不可挂题/刷题/AI 导入 |
| `LEAF` | 叶子题库，可挂题、刷题、导入；`bankId` / `nodeId` 均指 LEAF |

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/roots?scope=public\|mine` | `public` 公开；`mine` 须 `PREMIUM+` | 分页查询根节点（大厅入口） |
| `GET` | `/tree?scope=public\|mine&rootId=` | 同上 | 扁平树列表，前端自行组树 |
| `GET` | `/{nodeId}` | `PREMIUM+` | 节点详情（含 `childCount` 等统计） |
| `POST` | `/` | `PREMIUM+` | 创建 FOLDER 或 LEAF |
| `PUT` | `/{nodeId}` | `PREMIUM+` | 更新节点 |
| `DELETE` | `/{nodeId}` | `PREMIUM+` | 删除节点（FOLDER 递归删子树） |
| `PATCH` | `/{nodeId}/move` | `PREMIUM+` | 移动节点，防环校验 |
| `GET` | `/{nodeId}/hot-practice-detail` | 公开 | 公开 LEAF 热点刷题缓存 |
| `GET` | `/{nodeId}/questions` | `PREMIUM+` | LEAF 下试题分页 |
| `POST` | `/{nodeId}/questions` | `PREMIUM+` | LEAF 下新增试题 |
| `POST` | `/{nodeId}/questions/batch` | `PREMIUM+` | AI 批量入库 |

创建节点请求体示例：

```json
{
  "parentId": null,
  "nodeKind": "FOLDER",
  "title": "高等数学",
  "description": "",
  "isPublic": 0,
  "sortNo": 0
}
```

移动节点请求体：

```json
{
  "newParentId": 5,
  "newSortNo": 0
}
```

说明：

- 旧路径 `/api/v1/question-banks/*` 仍可用，内部映射为 **根级 LEAF** 的兼容操作。
- 题目仍存于单表 `question`，`question_bank_id` 指向 LEAF 节点 id。
- 本地开发需重建库：执行 `sql/schema/init_core_tables.sql`。

## 试题管理

Base path：`/api/v1/questions`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/{id}` | `PREMIUM+` | 获取试题详情 |
| `PUT` | `/{id}` | `PREMIUM+` | 全量更新试题 |
| `DELETE` | `/{id}` | `PREMIUM+` | 逻辑删除试题 |

新增或更新试题请求体：

```json
{
  "questionType": "SINGLE",
  "stem": "TCP 属于哪一层协议？",
  "optionsJson": "[\"应用层\",\"传输层\",\"网络层\",\"数据链路层\"]",
  "answerJson": "[\"B\"]",
  "analysis": "TCP 是传输层协议。",
  "sortNo": 1
}
```

题型枚举：

| 值 | 说明 |
| --- | --- |
| `SINGLE` | 单选题 |
| `MULTI` | 多选题 |
| `JUDGE` | 判断题 |
| `SHORT_ANSWER` | 简答题 |

注意：

- `optionsJson` 和 `answerJson` 是 JSON 数组字符串。
- 简答题通常使用 `optionsJson="[]"`，答案可按要点数组保存。
- 通过 `POST /api/v1/question-banks/{bankId}/questions` 新增时，所属题库以路径 `bankId` 为准。

## 在线刷题

Base path：`/api/v1/practice`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/banks/{bankId}/questions` | `USER+` | 获取刷题题目列表，不返回答案和解析 |
| `POST` | `/banks/{bankId}/questions/{questionId}/submit` | `USER+` | 提交答案并获取判分结果 |
| `GET` | `/banks/{bankId}/questions/{questionId}/reference` | `USER+` | 查看简答题参考答案 |

获取刷题列表参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `random` | boolean | 否 | 是否随机打乱顺序，默认 `false` |

提交答案请求体：

```json
{
  "userAnswer": ["A"]
}
```

答案格式：

- 单选：`["A"]`
- 多选：`["A", "C"]`
- 判断：`["T"]` 或 `["F"]`
- 简答题不使用提交接口，使用 reference 接口查看参考答案。

访问规则：

- 公开题库：任意登录用户可刷。
- 私有题库：仅题库所有者且 `PREMIUM+` 可刷，`ADMIN` 可绕过。
- 客观题答错后会自动进入错题本。

## 错题本

Base path：`/api/v1/wrong-questions`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/` | `USER+` | 分页查询当前用户错题 |
| `DELETE` | `/{id}` | `USER+` | 从错题本移除一条记录 |
| `GET` | `/practice` | `USER+` | 按错题本获取重刷题目列表 |

错题分页参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `current` | integer | 是 | 当前页 |
| `pageSize` | integer | 是 | 每页条数 |
| `bankId` | long | 否 | 按题库过滤 |

移除错题是逻辑删除。用户再次做错同一题时，错题记录会复活并递增错误次数。

## AI 智能导入

Base path：`/api/v1/ai-import`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/submit` | `PREMIUM+` | 上传文件并创建异步导入任务 |
| `GET` | `/tasks` | `PREMIUM+` | 分页查询当前用户 AI 导入任务 |
| `GET` | `/tasks/{taskId}/status` | `PREMIUM+` | 轮询任务状态与预览结果 |

提交导入任务使用 `multipart/form-data`：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | file | 是 | 支持 `.txt`、`.pdf`、`.docx`，最大 10MB |
| `bankId` | long | 是 | 目标题库 ID，必须有权限 |

任务列表参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `current` | integer | 是 | 当前页 |
| `pageSize` | integer | 是 | 每页条数 |
| `bankId` | long | 否 | 按题库过滤 |
| `status` | string | 否 | 多状态逗号分隔，如 `PARSED,PROCESSING` |
| `includePreview` | boolean | 否 | 是否在列表中携带预览题目 |

任务状态：

| 状态 | 说明 |
| --- | --- |
| `SUBMITTED` | 已提交，等待 Worker 消费 |
| `PROCESSING` | Worker 正在解析 |
| `PARSED` | 已解析，等待用户预览确认 |
| `IMPORTING` | 正在批量入库 |
| `IMPORTED` | 已导入正式题库 |
| `FAILED` | 解析失败 |
| `EXPIRED` | 长时间未确认，已过期 |

确认导入接口：

```http
POST /api/v1/question-banks/{bankId}/questions/batch
```

请求体：

```json
{
  "taskId": "a1b2c3d4e5f67890abcdef1234567890",
  "questions": [
    {
      "questionType": "SINGLE",
      "stem": "理想气体状态方程 PV=nRT 中，R 的数值是？",
      "options": ["8.31", "9.8", "6.02e23", "1.38e-23"],
      "answer": ["A"],
      "analysis": "R 为摩尔气体常数。"
    }
  ]
}
```

更多流程说明见 `AI_IMPORT_FLOW.md`。

## 管理端接口

### 用户管理

Base path：`/api/v1/admin/users`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/` | `ADMIN` | 分页查询用户 |
| `PUT` | `/{userId}/role` | `ADMIN` | 变更用户角色 |

用户分页参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `current` | integer | 是 | 当前页 |
| `pageSize` | integer | 是 | 每页条数 |
| `username` | string | 否 | 用户名模糊查询 |
| `role` | string | 否 | `USER`、`PREMIUM`、`ADMIN` |

变更角色请求体：

```json
{
  "role": "PREMIUM"
}
```

管理端仅允许把目标用户设置为 `USER` 或 `PREMIUM`，禁止设置 `ADMIN`，也禁止修改已有管理员。

### AI 导入运维

Base path：`/api/v1/admin/ai-import`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/stats` | `ADMIN` | 查看 AI 导入任务统计 |
| `POST` | `/tasks/cleanup` | `ADMIN` | 清理长时间未确认导入的 PARSED 任务 |

统计参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `days` | integer | 否 | 统计窗口天数，默认 30，范围 1-365 |

清理请求体：

```json
{
  "olderThanDays": 7,
  "bankId": 1001,
  "userId": 1,
  "dryRun": true,
  "deleteFiles": false,
  "maxBatch": 200
}
```

生产环境建议先使用 `dryRun=true` 查看命中数量和样例 `taskId`，确认后再执行 `dryRun=false`。
