package cn.heycloudream.ishua_backend.controller.admin;

import cn.heycloudream.ishua_backend.annotation.RequireRole;
import cn.heycloudream.ishua_backend.common.vo.Result;
import cn.heycloudream.ishua_backend.config.OpenApiConfig;
import cn.heycloudream.ishua_backend.config.openapi.ApiDocStandardResponses;
import cn.heycloudream.ishua_backend.dto.admin.AdminAiImportCleanupDTO;
import cn.heycloudream.ishua_backend.enums.UserRole;
import cn.heycloudream.ishua_backend.service.AiImportTaskService;
import cn.heycloudream.ishua_backend.vo.admin.AdminAiImportCleanupResultVO;
import cn.heycloudream.ishua_backend.vo.admin.AdminAiImportStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 AI 导入任务运维接口。
 *
 * @author C1ouD
 */
@RestController
@RequestMapping("/api/v1/admin/ai-import")
@RequiredArgsConstructor
@Validated
@Tag(name = "管理端 AI 导入任务", description = """
        须 JWT，仅 ADMIN 可访问；USER/PREMIUM 调用返回 code=403。
        用于运维：导入任务统计看板、清理长时间处于 PARSED 且未确认导入的解析草稿。
        """)
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiDocStandardResponses
@RequireRole(UserRole.ADMIN)
public class AdminAiImportController {

    private final AiImportTaskService aiImportTaskService;

    @GetMapping("/stats")
    @Operation(
            summary = "AI 导入任务统计看板",
            description = """
                    须 JWT，**仅 ADMIN**。基于 MySQL 聚合 `ai_import_task` 表，统计近 N 天导入流水线运行情况。

                    **查询参数**：
                    - **days**（可选，默认 30）：统计窗口天数，范围 1~365；以 `submitted_at >= now - days` 为过滤条件

                    **响应**（`AdminAiImportStatsVO`）：
                    - **periodDays / periodStart / periodEnd**：统计窗口元数据
                    - **totalTasks**：窗口内提交任务总数
                    - **statusStats[]**：各状态任务数及该状态下的平均流水线耗时（秒，Worker 实测 pipeline_duration_ms）
                    - **dailyAvgSubmitCount**：日均提交量（totalTasks / periodDays）
                    - **avgPipelineSeconds**：MinerU + LLM 平均总耗时（秒）
                    - **avgMineruSeconds** / **avgLlmSeconds**：分项平均耗时（秒）
                    - **avgParseSeconds**：同 avgPipelineSeconds（兼容字段）
                    - **avgQuestionCount**：平均解析题目数
                    - **failureRate**：失败率（FAILED / totalTasks，0~1）

                    **失败**：code=401 未登录；code=403 非 ADMIN；code=400（days 越界）
                    """)
    public Result<AdminAiImportStatsVO> getStats(
            @Parameter(description = "统计窗口天数，默认 30", example = "30")
            @RequestParam(value = "days", defaultValue = "30")
            @Min(value = 1, message = "统计天数至少为 1 天")
            @Max(value = 365, message = "统计天数不能超过 365 天")
            int days) {
        return Result.success(aiImportTaskService.getStats(days));
    }

    @PostMapping("/tasks/cleanup")
    @Operation(
            summary = "清理长时间未确认的 AI 导入任务",
            description = """
                    须 JWT，**仅 ADMIN**。扫描 MySQL 中 `status=PARSED` 且 `parsed_at` 早于阈值的记录。

                    **请求体**（`AdminAiImportCleanupDTO`，JSON）：
                    - **olderThanDays**（可选，默认 7）：清理 `parsed_at < now - N 天` 的任务；范围 1~365
                    - **bankId**（可选）：仅处理指定题库
                    - **userId**（可选）：仅处理指定提交用户
                    - **dryRun**（可选，**默认 true**）：true 时只统计匹配数与样例 taskId，**不写库、不删文件**
                    - **deleteFiles**（可选，**默认 false**）：dryRun=false 时，是否删除 `file_url` 对应的上传文件
                    - **maxBatch**（可选，默认 200）：单次最多处理条数，范围 1~1000

                    **dryRun=false 时的副作用**：
                    - DB：`status` → `EXPIRED`，写入 `expired_at`、`error_message`，清空 `preview_json`
                    - Redis：删除 `ishua:task:meta/status/result/import_lock` 相关键
                    - 用户侧：该 taskId 再调 batch 将返回 code=400「任务已过期」

                    **响应**（`AdminAiImportCleanupResultVO`）：
                    - `dryRun`、`matchedCount`、`processedCount`、`sampleTaskIds`（最多 10 条）、`message`

                    **运维建议**：生产环境先 `dryRun=true` 确认 `matchedCount` 与 `sampleTaskIds`，再 `dryRun=false` 实清。

                    **失败**：code=401 未登录；code=403 非 ADMIN；code=400（参数校验失败，如 olderThanDays 越界）
                    """)
    public Result<AdminAiImportCleanupResultVO> cleanupStaleParsedTasks(
            @Valid @RequestBody AdminAiImportCleanupDTO body) {
        return Result.success(aiImportTaskService.cleanupStaleParsed(body));
    }
}
