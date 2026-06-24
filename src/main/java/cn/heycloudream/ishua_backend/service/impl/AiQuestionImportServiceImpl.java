package cn.heycloudream.ishua_backend.service.impl;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 智能导入门面：负责入参校验、归属权校验、文件落盘、写元数据、写任务状态、发 Redis Stream。
 * <p>
 * 严格遵循 docs/Background.md 中的流程 A：Java 仅作为生产者，
 * 文档解析（MinerU）与大模型调用全部交给 ai-import-worker Python Worker。
 * 使用原生 {@link StringRedisTemplate} 发 XADD 到 {@code ishua:task:stream}。
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
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
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
        String metaJson;
        try {
            metaJson = objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            log.error("[submitFileImport] 序列化任务元数据失败 taskId={}", taskId, e);
            throw new BusinessException(500, "任务序列化失败", e);
        }
        stringRedisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .ofMap(Map.of("taskType", "ai.import.file", "taskId", taskId, "meta", metaJson))
                        .withStreamKey(IShuaRedisCacheConstants.TASK_STREAM_KEY));
        stringRedisTemplate.opsForStream().trim(IShuaRedisCacheConstants.TASK_STREAM_KEY, IShuaRedisCacheConstants.TASK_STREAM_MAX_LEN);
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
