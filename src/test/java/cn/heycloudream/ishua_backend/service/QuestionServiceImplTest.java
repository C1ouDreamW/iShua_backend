package cn.heycloudream.ishua_backend.service;

import cn.heycloudream.ishua_backend.dto.question.QuestionUpdateDTO;
import cn.heycloudream.ishua_backend.entity.BankNode;
import cn.heycloudream.ishua_backend.entity.Question;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.mapper.QuestionMapper;
import cn.heycloudream.ishua_backend.service.cache.QuestionBankDetailCacheEvictor;
import cn.heycloudream.ishua_backend.service.guard.BankAccessGuard;
import cn.heycloudream.ishua_backend.service.impl.QuestionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QuestionServiceImpl} 归属校验与缓存驱逐单元测试。
 */
@ExtendWith(MockitoExtension.class)
class QuestionServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long BANK_ID = 10L;
    private static final Long QUESTION_ID = 100L;

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private QuestionBankDetailCacheEvictor questionBankDetailCacheEvictor;

    @Mock
    private BankAccessGuard bankAccessGuard;

    @Mock
    private BankNodeService bankNodeService;

    private QuestionServiceImpl questionService;

    @BeforeEach
    void initService() {
        questionService = new QuestionServiceImpl(
                questionMapper,
                questionBankDetailCacheEvictor,
                bankAccessGuard,
                bankNodeService,
                new ObjectMapper());
    }

    @Test
    @DisplayName("getQuestionById: 试题不存在 → 404")
    void getQuestionById_notFound_shouldThrow404() {
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(null);

        assertThatThrownBy(() -> questionService.getQuestionById(USER_ID, QUESTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("getQuestionById: 非题库所有者 → 404")
    void getQuestionById_notOwner_shouldThrow404() {
        Question q = Question.builder().id(QUESTION_ID).questionBankId(BANK_ID).stem("题干").build();
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(q);
        doThrow(new BusinessException(404, "题库不存在或无权访问"))
                .when(bankAccessGuard).requireOwnedBank(USER_ID, BANK_ID);

        assertThatThrownBy(() -> questionService.getQuestionById(USER_ID, QUESTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("createQuestionInBank: 成功后驱逐缓存")
    void createQuestionInBank_success_shouldEvictCache() {
        when(bankAccessGuard.requireOwnedBank(USER_ID, BANK_ID))
                .thenReturn(BankNode.builder().id(BANK_ID).nodeKind("LEAF").build());
        when(questionMapper.insert(any(Question.class))).thenAnswer(inv -> {
            Question entity = inv.getArgument(0);
            entity.setId(QUESTION_ID);
            return 1;
        });

        QuestionUpdateDTO body = new QuestionUpdateDTO();
        body.setQuestionType("SINGLE");
        body.setStem("新题");
        body.setOptionsJson("[\"A\",\"B\"]");
        body.setAnswerJson("[\"A\"]");

        Long id = questionService.createQuestionInBank(USER_ID, BANK_ID, body);

        assertThat(id).isEqualTo(QUESTION_ID);
        verify(questionBankDetailCacheEvictor).evict(BANK_ID);
    }

    @Test
    @DisplayName("deleteQuestion: 成功后驱逐缓存")
    void deleteQuestion_success_shouldEvictCache() {
        Question q = Question.builder().id(QUESTION_ID).questionBankId(BANK_ID).build();
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(q);
        when(bankAccessGuard.requireOwnedBank(USER_ID, BANK_ID))
                .thenReturn(BankNode.builder().id(BANK_ID).nodeKind("LEAF").build());

        questionService.deleteQuestion(USER_ID, QUESTION_ID);

        verify(questionMapper).deleteById(QUESTION_ID);
        verify(questionBankDetailCacheEvictor).evict(BANK_ID);
    }

    @Test
    @DisplayName("batchImportPreview: 空列表不写库")
    void batchImportPreview_empty_shouldSkip() {
        when(bankAccessGuard.requireOwnedBank(USER_ID, BANK_ID))
                .thenReturn(BankNode.builder().id(BANK_ID).nodeKind("LEAF").build());

        questionService.batchImportPreview(USER_ID, BANK_ID, java.util.List.of());

        verify(questionMapper, never()).insert(any(Question.class));
        verify(questionBankDetailCacheEvictor, never()).evict(anyLong());
    }
}
