package cn.heycloudream.ishua_backend.service.impl;

import cn.heycloudream.ishua_backend.dto.practice.AnswerSubmitDTO;
import cn.heycloudream.ishua_backend.entity.Question;
import cn.heycloudream.ishua_backend.entity.BankNode;
import cn.heycloudream.ishua_backend.enums.BankNodeKind;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.service.BankNodeService;
import cn.heycloudream.ishua_backend.service.PracticeService;
import cn.heycloudream.ishua_backend.service.QuestionBankHotDetailService;
import cn.heycloudream.ishua_backend.service.QuestionService;
import cn.heycloudream.ishua_backend.service.WrongQuestionService;
import cn.heycloudream.ishua_backend.service.guard.BankAccessGuard;
import cn.heycloudream.ishua_backend.util.UserContextHolder;
import cn.heycloudream.ishua_backend.vo.practice.AnswerSubmitResultVO;
import cn.heycloudream.ishua_backend.vo.practice.PracticeQuestionVO;
import cn.heycloudream.ishua_backend.vo.practice.PracticeReferenceAnswerVO;
import cn.heycloudream.ishua_backend.vo.question.QuestionVO;
import cn.heycloudream.ishua_backend.vo.questionbank.QuestionBankDetailBundleVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 刷题业务服务实现。
 * <p>
 * 拉题策略：公开题库复用 {@link QuestionBankHotDetailService}（带 Redis 缓存），私有题库归属校验后直接查 DB。
 * 判分策略：SINGLE/JUDGE 比较首个答案（忽略大小写）；MULTI 排序后全量比较；
 * SHORT_ANSWER 不判分，通过 {@link #revealReferenceAnswer} 查看参考答案。
 * </p>
 *
 * @author C1ouD
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeServiceImpl implements PracticeService {

    private final QuestionBankHotDetailService questionBankHotDetailService;
    private final BankNodeService bankNodeService;
    private final QuestionService questionService;
    private final WrongQuestionService wrongQuestionService;
    private final BankAccessGuard bankAccessGuard;
    private final ObjectMapper objectMapper;

    @Override
    public List<PracticeQuestionVO> listPracticeQuestions(Long userId, Long bankId, boolean random) {
        BankNode bank = requirePracticeLeaf(bankId);

        List<PracticeQuestionVO> questions;
        if (isPublic(bank)) {
            // 公开题库：复用热点缓存，再剥离答案
            QuestionBankDetailBundleVO bundle = questionBankHotDetailService.getHotPublicBankDetail(bankId);
            questions = bundle.getQuestions().stream()
                    .map(this::toPracticeVO)
                    .collect(Collectors.toList());
        } else {
            // 私有题库：归属校验 + 查 DB（通过 QuestionService.listByBankId）
            bankAccessGuard.requirePrivatePracticeAccess(userId, UserContextHolder.getRole(), bank);
            questions = questionService.listByBankId(bankId).stream()
                    .map(this::entityToPracticeVO)
                    .collect(Collectors.toList());
        }

        if (random && questions.size() > 1) {
            List<PracticeQuestionVO> shuffled = new ArrayList<>(questions);
            Collections.shuffle(shuffled);
            return shuffled;
        }
        return questions;
    }

    @Override
    public AnswerSubmitResultVO submitAnswer(Long userId, Long bankId, Long questionId, AnswerSubmitDTO dto) {
        Question question = requireQuestionForPractice(userId, bankId, questionId);

        if (isShortAnswerType(question.getQuestionType())) {
            throw new BusinessException(400, "简答题不支持提交判分，请使用 GET .../reference 查看参考答案");
        }

        String questionType = question.getQuestionType();
        if (!isObjectiveType(questionType)) {
            return AnswerSubmitResultVO.builder()
                    .questionId(questionId)
                    .correct(null)
                    .needsManualGrading(true)
                    .answerJson(question.getAnswerJson())
                    .analysis(question.getAnalysis())
                    .build();
        }

        boolean correct = grade(questionType, dto.getUserAnswer(), question.getAnswerJson());
        if (!correct) {
            try {
                wrongQuestionService.recordWrong(userId, questionId);
            } catch (Exception e) {
                log.warn("[刷题] 写入错题本失败 userId={} questionId={}", userId, questionId, e);
            }
        }

        return AnswerSubmitResultVO.builder()
                .questionId(questionId)
                .correct(correct)
                .needsManualGrading(false)
                .answerJson(question.getAnswerJson())
                .analysis(question.getAnalysis())
                .build();
    }

    @Override
    public PracticeReferenceAnswerVO revealReferenceAnswer(Long userId, Long bankId, Long questionId) {
        Question question = requireQuestionForPractice(userId, bankId, questionId);
        if (!isShortAnswerType(question.getQuestionType())) {
            throw new BusinessException(400, "仅简答题支持查看参考答案，客观题请使用提交判分接口");
        }
        return PracticeReferenceAnswerVO.builder()
                .questionId(questionId)
                .questionType(question.getQuestionType())
                .answerJson(question.getAnswerJson())
                .analysis(question.getAnalysis())
                .build();
    }

    /**
     * 客观题判分：SINGLE/JUDGE 比较首个答案；MULTI 排序后全量比较。均忽略大小写。
     */
    private boolean grade(String questionType, List<String> userAnswer, String answerJson) {
        List<String> correctAnswer;
        try {
            correctAnswer = objectMapper.readValue(answerJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("[刷题] 解析标准答案 JSON 失败 answerJson={}", answerJson, e);
            throw new BusinessException(500, "试题答案数据异常，无法判分");
        }

        if (userAnswer == null || userAnswer.isEmpty()) {
            return false;
        }

        if ("MULTI".equalsIgnoreCase(questionType)) {
            // 多选题：数量必须一致，排序后逐一比较
            if (userAnswer.size() != correctAnswer.size()) {
                return false;
            }
            List<String> sortedUser = userAnswer.stream()
                    .map(String::toUpperCase)
                    .sorted()
                    .collect(Collectors.toList());
            List<String> sortedCorrect = correctAnswer.stream()
                    .map(String::toUpperCase)
                    .sorted()
                    .collect(Collectors.toList());
            return sortedUser.equals(sortedCorrect);
        }

        // SINGLE / JUDGE：只比较第一个答案
        if (correctAnswer.isEmpty()) {
            return false;
        }
        return userAnswer.get(0).equalsIgnoreCase(correctAnswer.get(0));
    }

    /**
     * 判断是否为简答题（不判分，仅查看参考答案）。
     */
    private boolean isShortAnswerType(String questionType) {
        return questionType != null && "SHORT_ANSWER".equalsIgnoreCase(questionType.trim());
    }

    private boolean isObjectiveType(String questionType) {
        if (questionType == null) {
            return false;
        }
        String upper = questionType.toUpperCase();
        return "SINGLE".equals(upper) || "MULTI".equals(upper) || "JUDGE".equals(upper);
    }

    private Question requireQuestionForPractice(Long userId, Long bankId, Long questionId) {
        Question question = questionService.getById(questionId);
        if (question == null) {
            throw new BusinessException(404, "试题不存在");
        }
        if (!bankId.equals(question.getQuestionBankId())) {
            throw new BusinessException(400, "试题不属于该题库");
        }
        BankNode bank = requirePracticeLeaf(bankId);
        if (!isPublic(bank)) {
            bankAccessGuard.requirePrivatePracticeAccess(userId, UserContextHolder.getRole(), bank);
        }
        return question;
    }

    private BankNode requirePracticeLeaf(Long bankId) {
        BankNode node = bankNodeService.requireExistsNode(bankId);
        if (!BankNodeKind.LEAF.name().equals(node.getNodeKind())) {
            throw new BusinessException(400, "仅题库节点支持刷题");
        }
        return node;
    }

    private boolean isPublic(BankNode bank) {
        return bank.getIsPublic() != null && bank.getIsPublic() == 1;
    }

    private PracticeQuestionVO toPracticeVO(QuestionVO vo) {
        return PracticeQuestionVO.builder()
                .id(vo.getId())
                .questionBankId(vo.getQuestionBankId())
                .questionType(vo.getQuestionType())
                .stem(vo.getStem())
                .optionsJson(vo.getOptionsJson())
                .sortNo(vo.getSortNo())
                .build();
    }

    private PracticeQuestionVO entityToPracticeVO(Question q) {
        return PracticeQuestionVO.builder()
                .id(q.getId())
                .questionBankId(q.getQuestionBankId())
                .questionType(q.getQuestionType())
                .stem(q.getStem())
                .optionsJson(q.getOptionsJson())
                .sortNo(q.getSortNo())
                .build();
    }
}
