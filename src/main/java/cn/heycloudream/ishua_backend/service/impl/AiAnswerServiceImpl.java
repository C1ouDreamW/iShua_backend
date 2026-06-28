package cn.heycloudream.ishua_backend.service.impl;

import cn.heycloudream.ishua_backend.dto.ai.AiAnswerCreateDTO;
import cn.heycloudream.ishua_backend.entity.AiAnswerTask;
import cn.heycloudream.ishua_backend.entity.AiImportTask;
import cn.heycloudream.ishua_backend.enums.AiAnswerTaskStatus;
import cn.heycloudream.ishua_backend.enums.AiImportTaskStatus;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.service.AiAnswerService;
import cn.heycloudream.ishua_backend.service.AiAnswerTaskService;
import cn.heycloudream.ishua_backend.service.AiImportTaskService;
import cn.heycloudream.ishua_backend.service.ai.AiAnswerResultStore;
import cn.heycloudream.ishua_backend.service.ai.AiAnswerStreamTaskDispatcher;
import cn.heycloudream.ishua_backend.service.ai.AiAnswerTaskMetaStore;
import cn.heycloudream.ishua_backend.service.ai.AiAnswerTaskStatusStore;
import cn.heycloudream.ishua_backend.util.TaskIdGenerator;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerSubmitVO;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerTaskMetaVO;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerTaskStatusVO;
import cn.heycloudream.ishua_backend.vo.ai.QuestionPreviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AI 解答业务编排实现（阶段 2）。
 *
 * @author C1ouD
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnswerServiceImpl implements AiAnswerService {

    /**
     * 仅支持客观题 AI 解答；简答题不进入此流程。
     */
    private static final Set<String> SUPPORTED_QTYPES = Set.of("SINGLE", "MULTI", "JUDGE");

    private final AiImportTaskService aiImportTaskService;
    private final AiAnswerTaskService aiAnswerTaskService;
    private final AiAnswerTaskMetaStore metaStore;
    private final AiAnswerTaskStatusStore statusStore;
    private final AiAnswerResultStore resultStore;
    private final AiAnswerStreamTaskDispatcher dispatcher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiAnswerSubmitVO createAnswerTask(Long currentUserId, String parentTaskId, AiAnswerCreateDTO dto) {
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录或用户上下文缺失");
        }
        if (dto == null || (isEmpty(dto.getQuestionIndices()) && isBlank(dto.getFilter()))) {
            throw new BusinessException(400, "必须指定 questionIndices 或 filter=MISSING");
        }

        AiImportTask parentTask = aiImportTaskService.findByTaskId(parentTaskId)
                .orElseThrow(() -> new BusinessException(404, "父导入任务不存在"));
        if (!currentUserId.equals(parentTask.getUserId())) {
            throw new BusinessException(403, "无权操作他人任务");
        }
        if (!AiImportTaskStatus.PARSED.name().equals(parentTask.getStatus())) {
            throw new BusinessException(400, "仅 PARSED 状态的导入任务可触发 AI 解答");
        }

        List<QuestionPreviewVO> preview = aiImportTaskService.readPreviewQuestions(parentTaskId)
                .orElseThrow(() -> new BusinessException(400, "导入任务预览数据缺失，请刷新重试"));

        List<QuestionPreviewVO> targets = selectTargets(preview, dto);
        if (targets.isEmpty()) {
            throw new BusinessException(400, "未筛选到符合条件的客观题（仅支持 SINGLE/MULTI/JUDGE 的 MISSING 题）");
        }

        String answerTaskId = TaskIdGenerator.generate();
        long now = System.currentTimeMillis();

        AiAnswerTask task = AiAnswerTask.builder()
                .answerTaskId(answerTaskId)
                .parentTaskId(parentTaskId)
                .userId(currentUserId)
                .bankId(parentTask.getBankId())
                .questionCount(targets.size())
                .answeredCount(0)
                .status(AiAnswerTaskStatus.SUBMITTED.name())
                .submittedAt(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault()))
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(0)
                .build();
        aiAnswerTaskService.createOnSubmit(task);

        AiAnswerTaskMetaVO meta = AiAnswerTaskMetaVO.builder()
                .answerTaskId(answerTaskId)
                .parentTaskId(parentTaskId)
                .userId(currentUserId)
                .bankId(parentTask.getBankId())
                .questions(targets)
                .submittedAt(now)
                .build();
        metaStore.write(answerTaskId, meta);
        dispatcher.dispatch(meta);
        statusStore.write(answerTaskId, AiAnswerTaskStatus.SUBMITTED, null, targets.size(), 0);

        log.info("[createAnswerTask] 解答任务已提交 answerTaskId={} parentTaskId={} questionCount={}",
                answerTaskId, parentTaskId, targets.size());

        return AiAnswerSubmitVO.builder()
                .answerTaskId(answerTaskId)
                .parentTaskId(parentTaskId)
                .status(AiAnswerTaskStatus.SUBMITTED.name())
                .questionCount(targets.size())
                .build();
    }

    @Override
    public AiAnswerTaskStatusVO getStatus(Long currentUserId, String parentTaskId, String answerTaskId) {
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录或用户上下文缺失");
        }
        AiAnswerTask dbTask = aiAnswerTaskService.findByAnswerTaskId(answerTaskId)
                .orElseThrow(() -> new BusinessException(404, "解答任务不存在"));
        if (!currentUserId.equals(dbTask.getUserId())) {
            throw new BusinessException(403, "无权访问该解答任务");
        }
        if (parentTaskId != null && !parentTaskId.equals(dbTask.getParentTaskId())) {
            throw new BusinessException(400, "解答任务与父导入任务不匹配");
        }

        AiAnswerTaskStatusVO vo = aiAnswerTaskService.buildStatus(answerTaskId).orElse(null);
        if (vo == null) {
            return null;
        }

        // DB 中已 ANSWERED/PARTIAL 但 answered_json 为空时，回填 Redis result 并异步同步
        if ((AiAnswerTaskStatus.ANSWERED.name().equals(vo.getStatus())
                || AiAnswerTaskStatus.PARTIAL.name().equals(vo.getStatus()))
                && vo.getQuestions() == null) {
            resultStore.readQuestions(answerTaskId).ifPresent(questions -> {
                vo.setQuestions(questions);
                vo.setAnsweredCount(questions.size());
                aiAnswerTaskService.syncStatusFromRedis(answerTaskId, vo, questions);
            });
        }
        return vo;
    }

    /**
     * 从 preview 中筛选待解答题目。
     * <p>
     * 若 dto.questionIndices 非空，按 下标 取题并校验为 MISSING 客观题；
     * 否则按 filter=MISSING 取全部 MISSING 客观题。
     * </p>
     */
    private List<QuestionPreviewVO> selectTargets(List<QuestionPreviewVO> preview, AiAnswerCreateDTO dto) {
        if (!isEmpty(dto.getQuestionIndices())) {
            Set<Integer> seen = new HashSet<>();
            List<QuestionPreviewVO> out = new ArrayList<>();
            for (Integer idx : dto.getQuestionIndices()) {
                if (idx == null || idx < 0 || idx >= preview.size()) {
                    throw new BusinessException(400, "题目下标越界：" + idx);
                }
                if (!seen.add(idx)) {
                    continue;
                }
                QuestionPreviewVO q = preview.get(idx);
                if (!isAnswerable(q)) {
                    throw new BusinessException(400,
                            "下标 " + idx + " 不是可解答的客观题（仅 SINGLE/MULTI/JUDGE 的 MISSING 题支持）");
                }
                out.add(q);
            }
            return out;
        }

        if ("MISSING".equalsIgnoreCase(dto.getFilter())) {
            List<QuestionPreviewVO> out = new ArrayList<>();
            for (QuestionPreviewVO q : preview) {
                if (isAnswerable(q)) {
                    out.add(q);
                }
            }
            return out;
        }

        throw new BusinessException(400, "不支持的 filter 值：" + dto.getFilter() + "（仅 MISSING）");
    }

    /**
     * 是否可被 AI 解答：客观题且 answerSource=MISSING。
     */
    private static boolean isAnswerable(QuestionPreviewVO q) {
        if (q == null || q.getQuestionType() == null) {
            return false;
        }
        return SUPPORTED_QTYPES.contains(q.getQuestionType())
                && "MISSING".equalsIgnoreCase(q.getAnswerSource());
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
