package cn.heycloudream.ishua_backend.controller;

import cn.heycloudream.ishua_backend.common.vo.Result;
import cn.heycloudream.ishua_backend.common.vo.PageResultVO;
import cn.heycloudream.ishua_backend.annotation.RequireRole;
import cn.heycloudream.ishua_backend.config.OpenApiConfig;
import cn.heycloudream.ishua_backend.config.openapi.ApiDocStandardResponses;
import cn.heycloudream.ishua_backend.dto.ai.AiImportTaskPageQueryDTO;
import cn.heycloudream.ishua_backend.entity.AiImportTask;
import cn.heycloudream.ishua_backend.enums.UserRole;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.service.AiImportTaskService;
import cn.heycloudream.ishua_backend.service.AiQuestionImportService;
import cn.heycloudream.ishua_backend.service.ai.AiImportRateLimiter;
import cn.heycloudream.ishua_backend.service.ai.AiImportResultStore;
import cn.heycloudream.ishua_backend.service.ai.AiImportTaskMetaStore;
import cn.heycloudream.ishua_backend.service.ai.AiImportTaskStatusStore;
import cn.heycloudream.ishua_backend.util.UserContextHolder;
import cn.heycloudream.ishua_backend.vo.ai.AiImportSubmitVO;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskMetaVO;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskStatusVO;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 智能导入控制器（流程 A 生产者侧 + 状态轮询）。
 * <p>
 * 文档解析与大模型调用由 ai-import-worker Worker 完成，Java 仅负责落盘 / 入 Stream / 提供任务状态。
 * </p>
 *
 * @author C1ouD
 */
@RestController
@RequestMapping("/api/v1/ai-import")
@RequiredArgsConstructor
@Validated
@Tag(name = "AI 智能导入", description = """
        须 JWT，最低角色 PREMIUM（ADMIN 含）；USER 调用返回 code=403。
        典型流程：submit → 轮询 status 或列表 tasks → PARSED 预览 → batch 确认入库。
        任务状态与预览以 MySQL `ai_import_task` 为可恢复权威源，Redis 作热路径缓存。
        """)
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiDocStandardResponses
@RequireRole(UserRole.PREMIUM)
public class AiImportController {

    private final AiQuestionImportService aiQuestionImportService;
    private final AiImportTaskStatusStore statusStore;
    private final AiImportResultStore resultStore;
    private final AiImportTaskMetaStore metaStore;
    private final AiImportRateLimiter rateLimiter;
    private final AiImportTaskService aiImportTaskService;

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "提交文件导入任务（异步）",
            description = """
                    须 JWT，最低角色 PREMIUM（ADMIN 含）。`multipart/form-data` 字段：
                    - **file**（必填）：.txt / .pdf / .docx，最大 10MB
                    - **bankId**（必填）：目标题库 ID（form 字段；亦可通过 query `?bankId=` 传递，二者等价）

                    成功立即返回 taskId（status=SUBMITTED），同时写入 MySQL 任务表；后台经 Redis Stream 派发至 Python Worker。
                    关页恢复：`GET /api/v1/ai-import/tasks` 分页列出本人任务。
                    轮询：`GET /api/v1/ai-import/tasks/{taskId}/status`。
                    预览确认入库：`POST /api/v1/question-banks/{bankId}/questions/batch`。

                    失败：code=400（文件/格式/大小）、401 未登录、403（角色为 USER 或题库无权）、404 题库不存在、429（默认每用户每小时 5 次，见配置 ishua.ai-import.rate-limit）。
                    """)
    public Result<AiImportSubmitVO> submitImport(
            @Parameter(
                    description = "导入文件，支持 txt / pdf / docx，最大 10MB",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "目标题库 ID（须为当前用户所属题库）", required = true, example = "1001")
            @RequestParam("bankId") Long bankId) {
        Long userId = UserContextHolder.get();
        rateLimiter.checkAndConsume(userId);
        AiImportSubmitVO vo = aiQuestionImportService.submitFileImport(userId, bankId, file);
        return Result.success(vo);
    }

    @GetMapping("/tasks")
    @Operation(
            summary = "分页查询当前用户 AI 导入任务",
            description = """
                    须 JWT，最低角色 PREMIUM（ADMIN 含）。用于页面重新打开后恢复「进行中 / 待确认」任务，无需本地保存 taskId。

                    **Query 参数**（`AiImportTaskPageQueryDTO`，均通过 query 传递）：
                    - **current**、**pageSize**（必填）：分页，pageSize 上限见全局校验（默认最大 100）
                    - **bankId**（可选）：仅返回指定题库下的任务
                    - **status**（可选）：逗号分隔多状态，如 `PARSED,PROCESSING`；合法值：
                      `SUBMITTED`、`PROCESSING`、`PARSED`、`IMPORTING`、`IMPORTED`、`FAILED`、`EXPIRED`
                    - **includePreview**（可选，默认 false）：为 true 时，`status=PARSED` 的摘要项携带 `questions[]`（QuestionPreviewVO）

                    **响应**：`Result<PageResultVO<AiImportTaskSummaryVO>>`
                    - `data.total`：总条数
                    - `data.records[]`：当前页摘要（taskId、bankId、fileName、status、message、questionCount、时间戳等）

                    **前端建议**：
                    - 导入页 onMounted 调用：`status=PARSED,PROCESSING,SUBMITTED`，展示「待确认 / 解析中」横幅
                    - `includePreview=true` 时响应体较大，建议 `pageSize` 不超过 10

                    **失败**：code=401 未登录；code=403 角色为 USER；code=400（status 含非法枚举值）
                    """)
    public Result<PageResultVO<AiImportTaskSummaryVO>> pageMyTasks(
            @ParameterObject @Valid @ModelAttribute AiImportTaskPageQueryDTO query) {
        Long userId = UserContextHolder.get();
        return Result.success(aiImportTaskService.pageForUser(userId, query));
    }

    @GetMapping("/tasks/{taskId}/status")
    @Operation(
            summary = "轮询 AI 导入任务状态",
            description = """
                    须 JWT，最低角色 PREMIUM（ADMIN 含）。

                    **状态流转**：SUBMITTED → PROCESSING → PARSED →（用户确认 batch）→ IMPORTED；
                    失败为 FAILED；长时间未确认可被管理端清理为 EXPIRED（不可再 batch）。

                    **数据优先级**（MySQL 持久化后）：
                    - 任务存在于 MySQL 时，以 DB 状态与 `preview_json` 为准
                    - DB 中 PARSED 但预览为空时，尝试从 Redis `ishua:task:result:{taskId}` 回填并异步同步回 DB
                    - 仅 Redis 存在、DB 尚未同步的极短窗口内，回退读 Redis meta/status

                    **响应字段**（`AiImportTaskStatusVO`）：
                    - **status**：见上；终态为 IMPORTED、FAILED、EXPIRED
                    - **message**：失败/过期原因或业务说明
                    - **totalCount**：解析题目数（PARSED 及之后有意义）
                    - **questions**：仅 status=PARSED 时填充（QuestionPreviewVO，options/answer 为数组）

                    **特殊响应**：
                    - 任务不存在或已彻底过期：code=200 且 **data=null**（非 HTTP 404）
                    - 无权访问他人任务：code=403

                    **权限**：仅任务提交者或 ADMIN 可读；USER 在类入口已拦截为 code=403。

                    **前端建议**：2~5 秒轮询；终态 IMPORTED / FAILED / EXPIRED 后停止；EXPIRED 提示用户重新上传。

                    **失败**：code=401 未登录；code=403 无权或角色为 USER
                    """)
    public Result<AiImportTaskStatusVO> getTaskStatus(
            @Parameter(description = "提交接口返回的任务 ID（UUID）", required = true)
            @PathVariable("taskId") String taskId) {
        Long userId = UserContextHolder.get();
        AiImportTask dbTask = aiImportTaskService.findByTaskId(taskId).orElse(null);
        if (dbTask != null) {
            if (!UserContextHolder.isAdmin() && !userId.equals(dbTask.getUserId())) {
                throw new BusinessException(403, "无权访问该任务");
            }
            AiImportTaskStatusVO vo = aiImportTaskService.buildStatus(taskId).orElse(null);
            if (vo != null && "PARSED".equals(vo.getStatus()) && vo.getQuestions() == null) {
                resultStore.readQuestions(taskId).ifPresent(questions -> {
                    vo.setQuestions(questions);
                    vo.setTotalCount(questions.size());
                    aiImportTaskService.syncStatusFromRedis(taskId, vo, questions);
                });
            }
            return Result.success(vo);
        }

        AiImportTaskMetaVO meta = metaStore.read(taskId).orElse(null);
        if (meta == null) {
            return Result.success(null);
        }
        if (!UserContextHolder.isAdmin() && !userId.equals(meta.getUserId())) {
            throw new BusinessException(403, "无权访问该任务");
        }

        AiImportTaskStatusVO vo = statusStore.read(taskId).orElse(null);
        if (vo == null) {
            return Result.success(null);
        }
        if ("PARSED".equals(vo.getStatus())) {
            resultStore.readQuestions(taskId).ifPresent(vo::setQuestions);
            vo.setTotalCount(vo.getQuestions() != null ? vo.getQuestions().size() : 0);
        }
        return Result.success(vo);
    }
}
