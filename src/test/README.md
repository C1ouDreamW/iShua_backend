# 测试基建说明

## Profile 与配置

| 文件 | 作用 |
|------|------|
| `application.yml` | 默认激活 `test` profile |
| `application-test.yml` | H2、固定 JWT、关闭调度、关闭 Swagger |
| `schema.sql` / `data.sql` | H2 建表与精简种子（`bankId=1`、`testuser`） |

集成测试使用 `@ActiveProfiles("test")` 或继承 `AbstractMockRedisSpringBootTest`。

## Redis 分层策略（A3）

| 场景 | 做法 |
|------|------|
| 默认（`mvn test`） | `AbstractMockRedisSpringBootTest` + `@MockBean StringRedisTemplate` |
| Cache-Aside 真 Redis | 继承 `RedisTestcontainersSupport`（需 Docker，无 Docker 时跳过） |

## 工具类（A4）

| 类 | 用途 |
|----|------|
| `JwtTestHelper` | 签发测试 Bearer Token（密钥与 `application-test.yml` 一致） |
| `UserContextTestSupport` | 设置/清理 `UserContextHolder` |
| `MockMvcTestSupport` | `MockMvc` 请求附加 `Authorization` 头 |
| `AtlasWebMvcTestConfig` | `@WebMvcTest` 时 `@Import` 拦截器与全局异常 |

## 编写约定

- **Service 业务逻辑**：纯 Mockito（参考 `PracticeServiceImplTest`），不启 Spring。
- **Controller / JWT / 校验**：`@WebMvcTest` + `@ContextConfiguration(classes = {IShuaTestApplication.class, XxxController.class})` + `AtlasWebMvcTestConfig` + `MockMvcTestSupport`（测试类在 `cn.heycloudream.ishua_test.controller`）。
- **Mapper / 全上下文**：`AbstractMockRedisSpringBootTest` 或 `RedisTestcontainersSupport`。
