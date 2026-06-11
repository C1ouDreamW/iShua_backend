package cn.heycloudream.ishua_backend.controller;

import cn.heycloudream.ishua_backend.annotation.RequireRole;
import cn.heycloudream.ishua_backend.common.vo.PageResultVO;
import cn.heycloudream.ishua_backend.common.vo.Result;
import cn.heycloudream.ishua_backend.config.OpenApiConfig;
import cn.heycloudream.ishua_backend.config.openapi.ApiDocPublicEndpoint;
import cn.heycloudream.ishua_backend.config.openapi.ApiDocStandardResponses;
import cn.heycloudream.ishua_backend.dto.ai.BatchImportRequestDTO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeCreateDTO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeMoveDTO;
import cn.heycloudream.ishua_backend.common.dto.PageRequestDTO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeSubtreeQueryDTO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeUpdateDTO;
import cn.heycloudream.ishua_backend.dto.question.QuestionInBankPageQueryDTO;
import cn.heycloudream.ishua_backend.dto.question.QuestionUpdateDTO;
import cn.heycloudream.ishua_backend.entity.AiImportTask;
import cn.heycloudream.ishua_backend.enums.AiImportTaskStatus;
import cn.heycloudream.ishua_backend.enums.UserRole;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.service.AiImportTaskService;
import cn.heycloudream.ishua_backend.service.BankNodeService;
import cn.heycloudream.ishua_backend.service.QuestionBankHotDetailService;
import cn.heycloudream.ishua_backend.service.QuestionService;
import cn.heycloudream.ishua_backend.service.ai.AiImportResultStore;
import cn.heycloudream.ishua_backend.service.ai.AiImportTaskMetaStore;
import cn.heycloudream.ishua_backend.service.ai.AiImportTaskStatusStore;
import cn.heycloudream.ishua_backend.service.ai.ImportIdempotentService;
import cn.heycloudream.ishua_backend.util.UserContextHolder;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskMetaVO;
import cn.heycloudream.ishua_backend.vo.ai.QuestionPreviewVO;
import cn.heycloudream.ishua_backend.vo.banknode.BankNodeVO;
import cn.heycloudream.ishua_backend.vo.question.QuestionVO;
import cn.heycloudream.ishua_backend.vo.questionbank.QuestionBankDetailBundleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题库树节点 REST 接口。
 *
 * @author C1ouD
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/bank-nodes")
@RequiredArgsConstructor
@Validated
@Tag(name = "题库树", description = "树形导航与节点管理；挂题/刷题仅 LEAF 节点")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiDocStandardResponses
public class BankNodeController {

    private final BankNodeService bankNodeService;
    private final QuestionService questionService;
    private final QuestionBankHotDetailService questionBankHotDetailService;
    private final ImportIdempotentService importIdempotentService;
    private final AiImportTaskStatusStore taskStatusStore;
    private final AiImportResultStore resultStore;
    private final AiImportTaskMetaStore taskMetaStore;
    private final AiImportTaskService aiImportTaskService;

    @GetMapping("/public/roots")
    @ApiDocPublicEndpoint
    @Operation(summary = "分页查询公开根节点", description = "无需登录，返回大厅可见根节点。")
    public Result<PageResultVO<BankNodeVO>> pagePublicRoots(
            @ParameterObject @Valid @ModelAttribute PageRequestDTO query) {
        return Result.success(bankNodeService.pagePublicRoots(query));
    }

    @GetMapping("/public/tree")
    @ApiDocPublicEndpoint
    @Operation(summary = "查询公开扁平题库树", description = """
            无需登录。返回扁平节点列表，前端自行组树；可选 rootId 限定子树。
            """)
    public Result<List<BankNodeVO>> listPublicTree(
            @ParameterObject @Valid @ModelAttribute BankNodeSubtreeQueryDTO query) {
        return Result.success(bankNodeService.listPublicTree(query));
    }

    @GetMapping("/mine/roots")
    @RequireRole(UserRole.PREMIUM)
    @Operation(summary = "分页查询当前用户根节点", description = "须 JWT，最低 PREMIUM（ADMIN 可）。")
    public Result<PageResultVO<BankNodeVO>> pageMyRoots(
            @ParameterObject @Valid @ModelAttribute PageRequestDTO query) {
        Long userId = UserContextHolder.get();
        return Result.success(bankNodeService.pageMyRoots(userId, query));
    }

    @GetMapping("/mine/tree")
    @RequireRole(UserRole.PREMIUM)
    @Operation(summary = "查询当前用户扁平题库树", description = """
            须 JWT，最低 PREMIUM（ADMIN 可）。返回扁平节点列表；可选 rootId 限定子树。
            """)
    public Result<List<BankNodeVO>> listMyTree(
            @ParameterObject @Valid @ModelAttribute BankNodeSubtreeQueryDTO query) {
        Long userId = UserContextHolder.get();
        return Result.success(bankNodeService.listMyTree(userId, query));
    }

    @GetMapping("/{nodeId}")
    @RequireRole(UserRole.PREMIUM)
    @Operation(summary = "查询节点详情", description = "须 PREMIUM+ 且为节点所有者（ADMIN 可 bypass）。")
    public Result<BankNodeVO> getNode(
            @Parameter(description = "节点 ID", required = true) @PathVariable("nodeId") Long nodeId) {
        Long userId = UserContextHolder.get();
        return Result.success(bankNodeService.getNode(userId, nodeId));
    }

    @PostMapping
    @RequireRole(UserRole.PREMIUM)
    @Operation(summary = "创建树节点", description = "nodeKind 为 FOLDER 或 LEAF；LEAF 可挂题。")
    public Result<Long> createNode(@Valid @RequestBody BankNodeCreateDTO dto) {
        Long userId = UserContextHolder.get();
        return Result.success(bankNodeService.createNode(userId, dto));
    }

    @PutMapping("/{nodeId}")
    @RequireRole(UserRole.PREMIUM)
    @Operation(summary = "更新树节点")
    public Result<Void> updateNode(
            @PathVariable("nodeId") Long nodeId,
            @Valid @RequestBody BankNodeUpdateDTO dto) {
        Long userId = UserContextHolder.get();
        bankNodeService.updateNode(userId, nodeId, dto);
        return Result.success(null);
    }

    @DeleteMapping("/{nodeId}")
    @RequireRole(UserRole.PREMIUM)
    @Operation(summary = "删除树节点", description = "FOLDER 递归删除子树；LEAF 级联删除题目。")
    public Result<Void> deleteNode(@PathVariable("nodeId") Long nodeId) {
        Long userId = UserContextHolder.get();
        bankNodeService.deleteNode(userId, nodeId);
        return Result.success(null);
    }

    @PatchMapping("/{nodeId}/move")
    @RequireRole(UserRole.PREMIUM)
    @Operation(summary = "移动树节点", description = "可调整父节点与同级排序；禁止形成环。")
    public Result<Void> moveNode(
            @PathVariable("nodeId") Long nodeId,
            @Valid @RequestBody BankNodeMoveDTO dto) {
        Long userId = UserContextHolder.get();
        bankNodeService.moveNode(userId, nodeId, dto);
        return Result.success(null);
    }

    @GetMapping("/{nodeId}/hot-practice-detail")
    @ApiDocPublicEndpoint
    @Operation(summary = "获取公开 LEAF 热点刷题聚合数据（Redis 缓存）")
    public Result<QuestionBankDetailBundleVO> getHotPracticeDetail(@PathVariable("nodeId") Long nodeId) {
        return Result.success(questionBankHotDetailService.getHotPublicBankDetail(nodeId));
    }

    @GetMapping("/{nodeId}/questions")
    @RequireRole(UserRole.PREMIUM)
    @Operation(summary = "分页查询 LEAF 节点下的试题")
    public Result<PageResultVO<QuestionVO>> pageQuestionsInNode(
            @PathVariable("nodeId") Long nodeId,
            @ParameterObject @Valid @ModelAttribute QuestionInBankPageQueryDTO query) {
        Long userId = UserContextHolder.get();
        return Result.success(questionService.pageQuestionsInBank(userId, nodeId, query));
    }

    @PostMapping("/{nodeId}/questions")
    @RequireRole(UserRole.PREMIUM)
    @Operation(summary = "在 LEAF 节点下新增试题")
    public Result<Long> createQuestionInNode(
            @PathVariable("nodeId") Long nodeId,
            @Valid @RequestBody QuestionUpdateDTO body) {
        Long userId = UserContextHolder.get();
        return Result.success(questionService.createQuestionInBank(userId, nodeId, body));
    }

    @PostMapping("/{nodeId}/questions/batch")
    @RequireRole(UserRole.PREMIUM)
    @Operation(summary = "批量确认导入 AI 解析题目（幂等）")
    public Result<Void> batchImportQuestions(
            @PathVariable("nodeId") Long nodeId,
            @Valid @RequestBody BatchImportRequestDTO body) {
        Long userId = UserContextHolder.get();
        String taskId = body.getTaskId();
        AiImportTask dbTask = aiImportTaskService.findByTaskId(taskId).orElse(null);
        if (dbTask != null) {
            if (AiImportTaskStatus.IMPORTED.name().equals(dbTask.getStatus())) {
                return Result.success(null);
            }
            if (AiImportTaskStatus.EXPIRED.name().equals(dbTask.getStatus())) {
                throw new BusinessException(400, "任务已过期，请重新上传文件");
            }
        }
        if (importIdempotentService.isAlreadyImported(taskId)) {
            return Result.success(null);
        }
        if (!importIdempotentService.tryAcquire(taskId)) {
            throw new BusinessException(409, "该任务正在导入中，请稍候再试");
        }
        boolean dbImported = false;
        try {
            if (dbTask != null) {
                if (!nodeId.equals(dbTask.getBankId())
                        || (!UserContextHolder.isAdmin() && !userId.equals(dbTask.getUserId()))) {
                    throw new BusinessException(403, "任务与题库不匹配或无权操作");
                }
            } else {
                AiImportTaskMetaVO meta = taskMetaStore.read(taskId)
                        .orElseThrow(() -> new BusinessException(400, "任务不存在或已过期"));
                if (!nodeId.equals(meta.getBankId())
                        || (!UserContextHolder.isAdmin() && !userId.equals(meta.getUserId()))) {
                    throw new BusinessException(403, "任务与题库不匹配或无权操作");
                }
            }
            if (dbTask != null && !AiImportTaskStatus.PARSED.name().equals(dbTask.getStatus())) {
                throw new BusinessException(400, "任务当前状态不可导入：" + dbTask.getStatus());
            }
            List<QuestionPreviewVO> previews = resolveImportPreviews(taskId, body.getQuestions());
            questionService.batchImportPreview(userId, nodeId, previews);
            dbImported = true;
            aiImportTaskService.markImported(taskId, previews.size());
            importIdempotentService.markImported(taskId);
            try {
                taskStatusStore.write(taskId, AiImportTaskStatus.IMPORTED,
                        "已导入 " + previews.size() + " 道题",
                        previews.size());
                resultStore.delete(taskId);
            } catch (Exception postCommitErr) {
                log.warn("[batchImport] 落库成功但状态/缓存清理失败 taskId={}", taskId, postCommitErr);
            }
            return Result.success(null);
        } catch (RuntimeException e) {
            if (!dbImported) {
                importIdempotentService.release(taskId);
            }
            throw e;
        }
    }

    private List<QuestionPreviewVO> resolveImportPreviews(String taskId, List<QuestionPreviewVO> fromBody) {
        return aiImportTaskService.readPreviewQuestions(taskId)
                .filter(cache -> !cache.isEmpty())
                .or(() -> resultStore.readQuestions(taskId))
                .filter(cache -> !cache.isEmpty())
                .map(cache -> {
                    if (fromBody.size() > cache.size()) {
                        log.warn("[batchImport] taskId={} 请求体题目数 {} 大于 Redis 缓存 {}，以缓存为准防重复落库",
                                taskId, fromBody.size(), cache.size());
                        return cache;
                    }
                    return fromBody;
                })
                .orElse(fromBody);
    }
}
