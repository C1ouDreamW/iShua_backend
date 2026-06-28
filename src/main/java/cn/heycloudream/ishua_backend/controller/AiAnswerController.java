package cn.heycloudream.ishua_backend.controller;

import cn.heycloudream.ishua_backend.annotation.RequireRole;
import cn.heycloudream.ishua_backend.common.vo.Result;
import cn.heycloudream.ishua_backend.config.OpenApiConfig;
import cn.heycloudream.ishua_backend.config.openapi.ApiDocStandardResponses;
import cn.heycloudream.ishua_backend.dto.ai.AiAnswerCreateDTO;
import cn.heycloudream.ishua_backend.enums.UserRole;
import cn.heycloudream.ishua_backend.service.AiAnswerService;
import cn.heycloudream.ishua_backend.util.UserContextHolder;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerSubmitVO;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerTaskStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 解答控制器（阶段 2）。
 * <p>
 * 独立流程：用户在预览页确认导入任务含 MISSING 客观题后，可触发 AI 解答；
 * 解答结果由独立 Python Worker（{@code answer_worker.py}）异步生成。
 * </p>
 *
 * @author C1ouD
 */
@RestController
@RequestMapping("/api/v1/ai-import/tasks/{taskId}/ai-answer")
@RequiredArgsConstructor
@Validated
@Tag(name = "AI 解答", description = """
        须 JWT，最低角色 PREMIUM（ADMIN 含）。
        触发 MISSING 客观题的 AI 解答（分片 + 投票），返回独立 answerTaskId 供轮询。
        解答结果合并到原 preview 后，仍走现有批量入库接口落库。
        """)
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiDocStandardResponses
@RequireRole(UserRole.PREMIUM)
public class AiAnswerController {

    private final AiAnswerService aiAnswerService;

    @PostMapping
    @Operation(
            summary = "创建 AI 解答任务",
            description = """
                    须 JWT，最低角色 PREMIUM（ADMIN 含）。

                    **前置条件**：父导入任务 `taskId` 必须为 `PARSED` 状态，且当前用户为提交者。

                    **请求体**（`AiAnswerCreateDTO`，二选一）：
                    - **questionIndices**：指定 preview_json 中的下标列表（0-based）
                    - **filter=MISSING**：自动筛选所有 answerSource=MISSING 且题型为 SINGLE/MULTI/JUDGE 的题

                    仅客观题（SINGLE/MULTI/JUDGE）的 MISSING 题进入解答流程；SHORT_ANSWER 不支持。
                    同时给出 questionIndices 与 filter 时以 questionIndices 为准。

                    **响应**（`AiAnswerSubmitVO`）：answerTaskId、parentTaskId、status=SUBMITTED、questionCount。

                    **失败**：code=400（参数非法/无可解答题/父任务非 PARSED）、403（无权操作）、404（父任务不存在）。
                    """)
    public Result<AiAnswerSubmitVO> createAnswerTask(
            @Parameter(description = "父导入任务 ID（ai_import_task.task_id）", required = true)
            @PathVariable("taskId") String taskId,
            @Valid @RequestBody AiAnswerCreateDTO dto) {
        Long userId = UserContextHolder.get();
        return Result.success(aiAnswerService.createAnswerTask(userId, taskId, dto));
    }

    @GetMapping("/{answerTaskId}/status")
    @Operation(
            summary = "轮询 AI 解答任务状态",
            description = """
                    须 JWT，最低角色 PREMIUM（ADMIN 含）。

                    **状态流转**：SUBMITTED → PROCESSING → ANSWERED（全部成功）/ PARTIAL（部分失败）/ FAILED（整体失败）→ IMPORTED（已入库）。

                    **数据优先级**：MySQL 为权威源；DB 中 ANSWERED/PARTIAL 但 answered_json 为空时回退读 Redis。

                    **响应字段**（`AiAnswerTaskStatusVO`）：
                    - **status**：见上；终态为 IMPORTED、FAILED
                    - **answeredCount**：成功解答数
                    - **totalCount**：待解答总数
                    - **questions**：仅 ANSWERED/PARTIAL 时返回，每项含 answerSource=AI_GENERATED 与 answerConfidence（HIGH/MEDIUM/LOW）

                    **前端建议**：2~5 秒轮询；ANSWERED/PARTIAL 后展示结果供用户二次确认，确认后调批量入库接口。

                    **失败**：code=403（无权访问）、404（任务不存在）。
                    """)
    public Result<AiAnswerTaskStatusVO> getStatus(
            @Parameter(description = "父导入任务 ID", required = true)
            @PathVariable("taskId") String taskId,
            @Parameter(description = "解答任务 ID", required = true)
            @PathVariable("answerTaskId") String answerTaskId) {
        Long userId = UserContextHolder.get();
        return Result.success(aiAnswerService.getStatus(userId, taskId, answerTaskId));
    }

    @GetMapping("/{answerTaskId}/result")
    @Operation(
            summary = "获取 AI 解答结果列表",
            description = """
                    须 JWT，最低角色 PREMIUM（ADMIN 含）。

                    返回 ANSWERED/PARTIAL 状态下的解答结果（QuestionPreviewVO[]），每项：
                    - **answerSource=AI_GENERATED**
                    - **answerConfidence**：HIGH/MEDIUM/LOW；LOW 题前端应高亮提醒用户必看
                    - **answer**：投票得出的答案
                    - **analysis**：可能含「【AI解答·存疑】」或「【AI解答·失败】」前缀

                    失败题目以 answer=[]、answerSource=MISSING、analysis 标「【AI解答·失败】」返回。

                    **失败**：code=403（无权访问）、404（任务不存在）、400（任务尚未完成）。
                    """)
    public Result<AiAnswerTaskStatusVO> getResult(
            @Parameter(description = "父导入任务 ID", required = true)
            @PathVariable("taskId") String taskId,
            @Parameter(description = "解答任务 ID", required = true)
            @PathVariable("answerTaskId") String answerTaskId) {
        Long userId = UserContextHolder.get();
        return Result.success(aiAnswerService.getStatus(userId, taskId, answerTaskId));
    }
}
