package cn.heycloudream.ishua_backend.service.impl;

import cn.heycloudream.ishua_backend.common.vo.PageResultVO;
import cn.heycloudream.ishua_backend.dto.question.QuestionInBankPageQueryDTO;
import cn.heycloudream.ishua_backend.dto.question.QuestionUpdateDTO;
import cn.heycloudream.ishua_backend.entity.Question;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.mapper.QuestionMapper;
import cn.heycloudream.ishua_backend.service.BankNodeService;
import cn.heycloudream.ishua_backend.service.QuestionService;
import cn.heycloudream.ishua_backend.service.cache.QuestionBankDetailCacheEvictor;
import cn.heycloudream.ishua_backend.service.guard.BankAccessGuard;
import cn.heycloudream.ishua_backend.vo.ai.QuestionPreviewVO;
import cn.heycloudream.ishua_backend.vo.question.QuestionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 试题服务实现。
 *
 * @author C1ouD
 */
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    private static final int SAVE_BATCH_SIZE = 500;

    private final QuestionBankDetailCacheEvictor questionBankDetailCacheEvictor;
    private final BankAccessGuard bankAccessGuard;
    private final BankNodeService bankNodeService;
    private final ObjectMapper objectMapper;

    public QuestionServiceImpl(
            QuestionMapper questionMapper,
            QuestionBankDetailCacheEvictor questionBankDetailCacheEvictor,
            BankAccessGuard bankAccessGuard,
            BankNodeService bankNodeService,
            ObjectMapper objectMapper) {
        this.baseMapper = questionMapper;
        this.questionBankDetailCacheEvictor = questionBankDetailCacheEvictor;
        this.bankAccessGuard = bankAccessGuard;
        this.bankNodeService = bankNodeService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveImportedQuestions(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        saveBatch(questions, SAVE_BATCH_SIZE);
        Map<Long, Long> countByBank = questions.stream()
                .map(Question::getQuestionBankId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
        countByBank.forEach((bankId, count) -> bankNodeService.adjustQuestionCount(bankId, count.intValue()));
        countByBank.keySet().forEach(questionBankDetailCacheEvictor::evict);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchImportPreview(Long currentUserId, Long bankId, List<QuestionPreviewVO> previews) {
        bankAccessGuard.requireOwnedBank(currentUserId, bankId);
        if (previews == null || previews.isEmpty()) {
            return;
        }
        List<QuestionPreviewVO> uniquePreviews = dedupePreviews(previews);
        LocalDateTime now = LocalDateTime.now();
        List<Question> entities = new ArrayList<>(uniquePreviews.size());
        int sortNo = nextSortNo(bankId);
        for (QuestionPreviewVO p : uniquePreviews) {
            String optionsJson;
            String answerJson;
            try {
                optionsJson = objectMapper.writeValueAsString(p.getOptions());
                answerJson = objectMapper.writeValueAsString(p.getAnswer());
            } catch (JsonProcessingException e) {
                throw new BusinessException(500, "序列化选项/答案 JSON 失败: " + e.getMessage());
            }
            Question q = Question.builder()
                    .questionBankId(bankId)
                    .questionType(p.getQuestionType().trim())
                    .stem(p.getStem().trim())
                    .optionsJson(optionsJson)
                    .answerJson(answerJson)
                    .analysis(p.getAnalysis() == null ? "" : p.getAnalysis())
                    .rawLlmJson(null)
                    .sortNo(sortNo++)
                    .createTime(now)
                    .updateTime(now)
                    .isDeleted(0)
                    .build();
            entities.add(q);
        }
        saveImportedQuestions(entities);
    }

    @Override
    public PageResultVO<QuestionVO> pageQuestionsInBank(Long currentUserId, Long bankId, QuestionInBankPageQueryDTO query) {
        bankAccessGuard.requireOwnedBank(currentUserId, bankId);
        Page<Question> mp = new Page<>(query.getCurrent(), query.getPageSize());
        LambdaQueryWrapper<Question> w = new LambdaQueryWrapper<Question>()
                .eq(Question::getQuestionBankId, bankId)
                .orderByAsc(Question::getSortNo)
                .orderByDesc(Question::getUpdateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            w.like(Question::getStem, query.getKeyword().trim());
        }
        baseMapper.selectPage(mp, w);
        List<QuestionVO> records = mp.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        return PageResultVO.<QuestionVO>builder().total(mp.getTotal()).records(records).build();
    }

    @Override
    public QuestionVO getQuestionById(Long currentUserId, Long questionId) {
        Question q = baseMapper.selectById(questionId);
        if (q == null) {
            throw new BusinessException(404, "试题不存在或无权访问");
        }
        bankAccessGuard.requireOwnedBank(currentUserId, q.getQuestionBankId());
        return toVo(q);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createQuestionInBank(Long currentUserId, Long bankId, QuestionUpdateDTO body) {
        bankAccessGuard.requireOwnedBank(currentUserId, bankId);
        validateQuestionPayload(body.getQuestionType(), body.getOptionsJson(), body.getAnswerJson());
        LocalDateTime now = LocalDateTime.now();
        Question entity = Question.builder()
                .questionBankId(bankId)
                .questionType(body.getQuestionType().trim())
                .stem(body.getStem().trim())
                .optionsJson(body.getOptionsJson())
                .answerJson(body.getAnswerJson())
                .analysis(body.getAnalysis() == null ? null : body.getAnalysis().trim())
                .rawLlmJson(null)
                .sortNo(body.getSortNo() == null ? 0 : body.getSortNo())
                .createTime(now)
                .updateTime(now)
                .isDeleted(0)
                .build();
        baseMapper.insert(entity);
        bankNodeService.adjustQuestionCount(bankId, 1);
        questionBankDetailCacheEvictor.evict(bankId);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuestion(Long currentUserId, Long questionId, QuestionUpdateDTO dto) {
        Question q = baseMapper.selectById(questionId);
        if (q == null) {
            throw new BusinessException(404, "试题不存在或无权访问");
        }
        bankAccessGuard.requireOwnedBank(currentUserId, q.getQuestionBankId());
        validateQuestionPayload(dto.getQuestionType(), dto.getOptionsJson(), dto.getAnswerJson());
        q.setQuestionType(dto.getQuestionType().trim());
        q.setStem(dto.getStem().trim());
        q.setOptionsJson(dto.getOptionsJson());
        q.setAnswerJson(dto.getAnswerJson());
        q.setAnalysis(dto.getAnalysis() == null ? null : dto.getAnalysis().trim());
        q.setSortNo(dto.getSortNo() == null ? q.getSortNo() : dto.getSortNo());
        q.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(q);
        questionBankDetailCacheEvictor.evict(q.getQuestionBankId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(Long currentUserId, Long questionId) {
        Question q = baseMapper.selectById(questionId);
        if (q == null) {
            throw new BusinessException(404, "试题不存在或无权访问");
        }
        Long bankId = q.getQuestionBankId();
        bankAccessGuard.requireOwnedBank(currentUserId, bankId);
        baseMapper.deleteById(questionId);
        bankNodeService.adjustQuestionCount(bankId, -1);
        questionBankDetailCacheEvictor.evict(bankId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeQuestionsByBankId(Long bankId) {
        remove(new LambdaQueryWrapper<Question>().eq(Question::getQuestionBankId, bankId));
        bankNodeService.resetQuestionCount(bankId, 0);
        questionBankDetailCacheEvictor.evict(bankId);
    }

    @Override
    public List<Question> listByBankId(Long bankId) {
        return baseMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getQuestionBankId, bankId)
                .orderByAsc(Question::getSortNo)
                .orderByDesc(Question::getUpdateTime));
    }

    /**
     * 按题干 + 题型 + 答案去重，保留首次出现顺序。
     */
    private List<QuestionPreviewVO> dedupePreviews(List<QuestionPreviewVO> previews) {
        Map<String, QuestionPreviewVO> ordered = new LinkedHashMap<>();
        for (QuestionPreviewVO p : previews) {
            if (p == null || p.getStem() == null || p.getStem().isBlank()) {
                continue;
            }
            ordered.putIfAbsent(dedupeKey(p), p);
        }
        return new ArrayList<>(ordered.values());
    }

    private String dedupeKey(QuestionPreviewVO p) {
        String type = p.getQuestionType() == null ? "" : p.getQuestionType().trim();
        String stem = p.getStem().trim();
        String answer;
        try {
            answer = objectMapper.writeValueAsString(p.getAnswer());
        } catch (JsonProcessingException e) {
            answer = String.valueOf(p.getAnswer());
        }
        return type + "\u0000" + stem + "\u0000" + answer;
    }

    private int nextSortNo(Long bankId) {
        Question max = baseMapper.selectOne(new LambdaQueryWrapper<Question>()
                .eq(Question::getQuestionBankId, bankId)
                .orderByDesc(Question::getSortNo)
                .last("LIMIT 1"));
        if (max == null || max.getSortNo() == null) {
            return 1;
        }
        return max.getSortNo() + 1;
    }

    private void validateQuestionPayload(String questionType, String optionsJson, String answerJson) {
        if (questionType == null || !"SHORT_ANSWER".equalsIgnoreCase(questionType.trim())) {
            return;
        }
        List<String> options = parseJsonStringList(optionsJson, "选项 JSON");
        if (!options.isEmpty()) {
            throw new BusinessException(400, "简答题 optionsJson 须为 []");
        }
        List<String> answers = parseJsonStringList(answerJson, "答案 JSON");
        if (answers.isEmpty() || answers.stream().anyMatch(a -> a == null || a.isBlank())) {
            throw new BusinessException(400, "简答题 answerJson 须为非空文本数组");
        }
    }

    private List<String> parseJsonStringList(String json, String fieldName) {
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return list == null ? List.of() : list;
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, fieldName + " 格式非法");
        }
    }

    private QuestionVO toVo(Question e) {
        return QuestionVO.builder()
                .id(e.getId())
                .questionBankId(e.getQuestionBankId())
                .questionType(e.getQuestionType())
                .stem(e.getStem())
                .optionsJson(e.getOptionsJson())
                .answerJson(e.getAnswerJson())
                .analysis(e.getAnalysis())
                .sortNo(e.getSortNo())
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }
}
