package cn.heycloudream.ishua_backend.service.impl;

import cn.heycloudream.ishua_backend.common.constants.ValidationConstants;
import cn.heycloudream.ishua_backend.enums.AiImportTaskStatus;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.service.AiImportTaskService;
import cn.heycloudream.ishua_backend.service.AiQuestionImportService;
import cn.heycloudream.ishua_backend.service.ai.AiImportTaskMetaStore;
import cn.heycloudream.ishua_backend.service.ai.AiImportTaskStatusStore;
import cn.heycloudream.ishua_backend.service.file.FileStorageService;
import cn.heycloudream.ishua_backend.service.guard.BankAccessGuard;
import cn.heycloudream.ishua_backend.util.TaskIdGenerator;
import cn.heycloudream.ishua_backend.vo.ai.AiImportSubmitVO;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskMetaVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * 智能导入门面：负责入参校验、归属权校验、文件落盘、写元数据、写任务状态。
 * <p>
 * 严格遵循 docs/Background.md 中的流程 A：Java 仅作为生产者，
 * 文档解析（MinerU）与大模型调用全部交给 ai-import-worker Worker。
 * StreamTask 发布已暂时移除，后续恢复时在此处重新 publish。
 * </p>
 *
 * @author C1ouD
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuestionImportServiceImpl implements AiQuestionImportService {

    private static final Set<String> ALLOWED_IMPORT_EXTENSIONS = Set.of("txt", "pdf", "docx");

    private final FileStorageService fileStorageService;

    private final AiImportTaskStatusStore taskStatusStore;
    private final AiImportTaskMetaStore taskMetaStore;
    private final AiImportTaskService aiImportTaskService;
    private final BankAccessGuard bankAccessGuard;

    @Override
    public AiImportSubmitVO submitFileImport(Long currentUserId, Long bankId, MultipartFile file) {
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录或用户上下文缺失");
        }
        if (bankId == null) {
            throw new BusinessException(400, "题库 ID 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        if (file.getSize() > ValidationConstants.FILE_IMPORT_MAX_SIZE_BYTES) {
            throw new BusinessException(400, "文件过大，最大支持 10 MB");
        }
        validateImportFilename(file.getOriginalFilename());
        bankAccessGuard.requireOwnedBank(currentUserId, bankId);

        String fileUrl;
        try {
            fileUrl = fileStorageService.store(file);
        } catch (IOException e) {
            log.error("[submitFileImport] 文件落盘失败 bankId={}", bankId, e);
            throw new BusinessException(500, "文件存储失败", e);
        }

        String taskId = TaskIdGenerator.generate();
        String originalFilename = file.getOriginalFilename();
        long now = System.currentTimeMillis();

        AiImportTaskMetaVO meta = AiImportTaskMetaVO.builder()
                .taskId(taskId)
                .userId(currentUserId)
                .bankId(bankId)
                .fileName(originalFilename)
                .fileSize(file.getSize())
                .fileUrl(fileUrl)
                .submittedAt(now)
                .type("file")
                .build();

        aiImportTaskService.createOnSubmit(meta);
        taskMetaStore.write(taskId, meta);
        // StreamTask 暂时移除，后续恢复时在此处重新 publish
        taskStatusStore.write(taskId, AiImportTaskStatus.SUBMITTED, null, null);

        log.info("[submitFileImport] 任务已提交 taskId={} bankId={} file={}", taskId, bankId, originalFilename);

        return AiImportSubmitVO.builder()
                .taskId(taskId)
                .status(AiImportTaskStatus.SUBMITTED.name())
                .build();
    }

    private static void validateImportFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(400, "文件名不能为空");
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new BusinessException(400, "文件名必须包含扩展名");
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMPORT_EXTENSIONS.contains(ext)) {
            throw new BusinessException(400, "不支持的文件格式：" + ext + "，仅支持 txt / pdf / docx");
        }
    }
}
