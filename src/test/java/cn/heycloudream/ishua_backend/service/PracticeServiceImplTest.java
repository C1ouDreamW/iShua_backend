package cn.heycloudream.ishua_backend.service;

import cn.heycloudream.ishua_backend.dto.practice.AnswerSubmitDTO;
import cn.heycloudream.ishua_backend.entity.BankNode;
import cn.heycloudream.ishua_backend.entity.Question;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.service.guard.BankAccessGuard;
import cn.heycloudream.ishua_backend.service.impl.PracticeServiceImpl;
import cn.heycloudream.ishua_backend.vo.practice.AnswerSubmitResultVO;
import cn.heycloudream.ishua_backend.vo.practice.PracticeQuestionVO;
import cn.heycloudream.ishua_backend.vo.practice.PracticeReferenceAnswerVO;
import cn.heycloudream.ishua_backend.vo.question.QuestionVO;
import cn.heycloudream.ishua_backend.vo.questionbank.QuestionBankDetailBundleVO;
import cn.heycloudream.ishua_backend.vo.questionbank.QuestionBankVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PracticeServiceImpl} 核心业务路径单元测试（纯 Mockito，无 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class PracticeServiceImplTest {

    @Mock
    private QuestionBankHotDetailService questionBankHotDetailService;

    @Mock
    private BankNodeService bankNodeService;

    @Mock
    private QuestionService questionService;

    @Mock
    private WrongQuestionService wrongQuestionService;

    @Mock
    private BankAccessGuard bankAccessGuard;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private PracticeServiceImpl practiceService;

    private static final Long USER_ID = 1L;
    private static final Long BANK_ID = 10L;
    private static final Long QUESTION_ID = 100L;

    private BankNode publicBank() {
        return BankNode.builder().id(BANK_ID).userId(999L).nodeKind("LEAF").isPublic(1).build();
    }

    private BankNode privateBank() {
        return BankNode.builder().id(BANK_ID).userId(USER_ID).nodeKind("LEAF").isPublic(0).build();
    }

    private Question buildQuestion(String type, String answerJson) {
        return Question.builder()
                .id(QUESTION_ID)
                .questionBankId(BANK_ID)
                .questionType(type)
                .stem("题干")
                .optionsJson("[\"A\",\"B\",\"C\",\"D\"]")
                .answerJson(answerJson)
                .analysis("解析文本")
                .isDeleted(0)
                .build();
    }

    @Test
    @DisplayName("listPracticeQuestions: 公开题库 → 复用热点缓存，返回无答案的题目")
    void listPracticeQuestions_publicBank_shouldUseHotDetailCache() {
        when(bankNodeService.requireExistsNode(BANK_ID)).thenReturn(publicBank());

        QuestionVO qVO = QuestionVO.builder()
                .id(QUESTION_ID).questionBankId(BANK_ID).questionType("SINGLE")
                .stem("示例题干").optionsJson("[\"A\",\"B\"]").answerJson("[\"A\"]").sortNo(1)
                .build();
        QuestionBankDetailBundleVO bundle = QuestionBankDetailBundleVO.builder()
                .bank(QuestionBankVO.builder().id(BANK_ID).build())
                .questions(List.of(qVO))
                .build();
        when(questionBankHotDetailService.getHotPublicBankDetail(BANK_ID)).thenReturn(bundle);

        List<PracticeQuestionVO> result = practiceService.listPracticeQuestions(USER_ID, BANK_ID, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(QUESTION_ID);
        assertThat(result.get(0).getStem()).isEqualTo("示例题干");
        verify(questionBankHotDetailService).getHotPublicBankDetail(BANK_ID);
        verify(questionService, never()).listByBankId(any());
    }

    @Test
    @DisplayName("listPracticeQuestions: 私有题库且为所有者 → 调 QuestionService.listByBankId，不走缓存")
    void listPracticeQuestions_privateBank_owner_shouldQueryDb() {
        when(bankNodeService.requireExistsNode(BANK_ID)).thenReturn(privateBank());
        when(questionService.listByBankId(BANK_ID)).thenReturn(List.of(buildQuestion("SINGLE", "[\"A\"]")));

        List<PracticeQuestionVO> result = practiceService.listPracticeQuestions(USER_ID, BANK_ID, false);

        assertThat(result).hasSize(1);
        verify(questionBankHotDetailService, never()).getHotPublicBankDetail(anyLong());
        verify(questionService).listByBankId(BANK_ID);
    }

    @Test
    @DisplayName("listPracticeQuestions: 私有题库非所有者 → 抛 403")
    void listPracticeQuestions_privateBank_notOwner_shouldThrow403() {
        when(bankNodeService.requireExistsNode(BANK_ID)).thenReturn(privateBank());
        doThrow(new BusinessException(403, "无权访问该题库"))
                .when(bankAccessGuard).requirePrivatePracticeAccess(anyLong(), any(), any());

        assertThatThrownBy(() -> practiceService.listPracticeQuestions(999L, BANK_ID, false))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("listPracticeQuestions: 题库不存在 → 抛 404")
    void listPracticeQuestions_bankNotFound_shouldThrow404() {
        when(bankNodeService.requireExistsNode(BANK_ID))
                .thenThrow(new BusinessException(404, "节点不存在"));

        assertThatThrownBy(() -> practiceService.listPracticeQuestions(USER_ID, BANK_ID, false))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("submitAnswer: SINGLE 题答对 → correct=true，不写错题本")
    void submitAnswer_singleCorrect_shouldNotRecordWrong() {
        when(questionService.getById(QUESTION_ID)).thenReturn(buildQuestion("SINGLE", "[\"A\"]"));
        when(bankNodeService.requireExistsNode(BANK_ID)).thenReturn(publicBank());

        AnswerSubmitDTO dto = AnswerSubmitDTO.builder().userAnswer(List.of("A")).build();
        AnswerSubmitResultVO result = practiceService.submitAnswer(USER_ID, BANK_ID, QUESTION_ID, dto);

        assertThat(result.getCorrect()).isTrue();
        assertThat(result.getNeedsManualGrading()).isFalse();
        assertThat(result.getAnswerJson()).isEqualTo("[\"A\"]");
        verify(wrongQuestionService, never()).recordWrong(any(), any());
    }

    @Test
    @DisplayName("submitAnswer: SINGLE 题答错 → correct=false，自动记录错题")
    void submitAnswer_singleWrong_shouldRecordWrong() {
        when(questionService.getById(QUESTION_ID)).thenReturn(buildQuestion("SINGLE", "[\"A\"]"));
        when(bankNodeService.requireExistsNode(BANK_ID)).thenReturn(publicBank());

        AnswerSubmitDTO dto = AnswerSubmitDTO.builder().userAnswer(List.of("B")).build();
        AnswerSubmitResultVO result = practiceService.submitAnswer(USER_ID, BANK_ID, QUESTION_ID, dto);

        assertThat(result.getCorrect()).isFalse();
        verify(wrongQuestionService).recordWrong(USER_ID, QUESTION_ID);
    }

    @Test
    @DisplayName("submitAnswer: JUDGE 题答对（忽略大小写）")
    void submitAnswer_judgeCorrect_caseInsensitive() {
        when(questionService.getById(QUESTION_ID)).thenReturn(buildQuestion("JUDGE", "[\"T\"]"));
        when(bankNodeService.requireExistsNode(BANK_ID)).thenReturn(publicBank());

        AnswerSubmitDTO dto = AnswerSubmitDTO.builder().userAnswer(List.of("t")).build();
        AnswerSubmitResultVO result = practiceService.submitAnswer(USER_ID, BANK_ID, QUESTION_ID, dto);

        assertThat(result.getCorrect()).isTrue();
    }

    @Test
    @DisplayName("submitAnswer: MULTI 题顺序不同但选项相同 → correct=true")
    void submitAnswer_multiCorrectDifferentOrder() {
        when(questionService.getById(QUESTION_ID)).thenReturn(buildQuestion("MULTI", "[\"A\",\"C\"]"));
        when(bankNodeService.requireExistsNode(BANK_ID)).thenReturn(publicBank());

        AnswerSubmitDTO dto = AnswerSubmitDTO.builder().userAnswer(List.of("C", "A")).build();
        AnswerSubmitResultVO result = practiceService.submitAnswer(USER_ID, BANK_ID, QUESTION_ID, dto);

        assertThat(result.getCorrect()).isTrue();
    }

    @Test
    @DisplayName("submitAnswer: MULTI 题少选 → correct=false")
    void submitAnswer_multiIncomplete_shouldBeFalse() {
        when(questionService.getById(QUESTION_ID)).thenReturn(buildQuestion("MULTI", "[\"A\",\"C\"]"));
        when(bankNodeService.requireExistsNode(BANK_ID)).thenReturn(publicBank());

        AnswerSubmitDTO dto = AnswerSubmitDTO.builder().userAnswer(List.of("A")).build();
        AnswerSubmitResultVO result = practiceService.submitAnswer(USER_ID, BANK_ID, QUESTION_ID, dto);

        assertThat(result.getCorrect()).isFalse();
    }

    @Test
    @DisplayName("submitAnswer: 简答题 → 抛 400")
    void submitAnswer_shortAnswer_shouldThrow400() {
        when(questionService.getById(QUESTION_ID)).thenReturn(buildQuestion("SHORT_ANSWER", "[\"参考答案\"]"));
        when(bankNodeService.requireExistsNode(BANK_ID)).thenReturn(publicBank());

        AnswerSubmitDTO dto = AnswerSubmitDTO.builder().userAnswer(List.of("我的回答")).build();
        assertThatThrownBy(() -> practiceService.submitAnswer(USER_ID, BANK_ID, QUESTION_ID, dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
        verify(wrongQuestionService, never()).recordWrong(any(), any());
    }

    @Test
    @DisplayName("revealReferenceAnswer: 简答题 → 返回参考答案")
    void revealReferenceAnswer_shortAnswer_shouldReturnReference() {
        when(questionService.getById(QUESTION_ID)).thenReturn(buildQuestion("SHORT_ANSWER", "[\"要点一\",\"要点二\"]"));
        when(bankNodeService.requireExistsNode(BANK_ID)).thenReturn(publicBank());

        PracticeReferenceAnswerVO result = practiceService.revealReferenceAnswer(USER_ID, BANK_ID, QUESTION_ID);

        assertThat(result.getQuestionId()).isEqualTo(QUESTION_ID);
        assertThat(result.getQuestionType()).isEqualTo("SHORT_ANSWER");
        assertThat(result.getAnswerJson()).isEqualTo("[\"要点一\",\"要点二\"]");
        assertThat(result.getAnalysis()).isEqualTo("解析文本");
    }

    @Test
    @DisplayName("revealReferenceAnswer: 客观题 → 抛 400")
    void revealReferenceAnswer_objective_shouldThrow400() {
        when(questionService.getById(QUESTION_ID)).thenReturn(buildQuestion("SINGLE", "[\"A\"]"));
        when(bankNodeService.requireExistsNode(BANK_ID)).thenReturn(publicBank());

        assertThatThrownBy(() -> practiceService.revealReferenceAnswer(USER_ID, BANK_ID, QUESTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("submitAnswer: 主观题 → needsManualGrading=true，不写错题本")
    void submitAnswer_subjectiveType_shouldMarkManualGrading() {
        when(questionService.getById(QUESTION_ID)).thenReturn(buildQuestion("ESSAY", "[\"参考答案\"]"));
        when(bankNodeService.requireExistsNode(BANK_ID)).thenReturn(publicBank());

        AnswerSubmitDTO dto = AnswerSubmitDTO.builder().userAnswer(List.of("我的回答")).build();
        AnswerSubmitResultVO result = practiceService.submitAnswer(USER_ID, BANK_ID, QUESTION_ID, dto);

        assertThat(result.getNeedsManualGrading()).isTrue();
        assertThat(result.getCorrect()).isNull();
        verify(wrongQuestionService, never()).recordWrong(any(), any());
    }

    @Test
    @DisplayName("submitAnswer: 试题不存在 → 抛 404")
    void submitAnswer_questionNotFound_shouldThrow404() {
        when(questionService.getById(QUESTION_ID)).thenReturn(null);

        AnswerSubmitDTO dto = AnswerSubmitDTO.builder().userAnswer(List.of("A")).build();
        assertThatThrownBy(() -> practiceService.submitAnswer(USER_ID, BANK_ID, QUESTION_ID, dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("submitAnswer: 试题所属题库与路径 bankId 不匹配 → 抛 400")
    void submitAnswer_questionBankMismatch_shouldThrow400() {
        when(questionService.getById(QUESTION_ID)).thenReturn(buildQuestion("SINGLE", "[\"A\"]"));

        AnswerSubmitDTO dto = AnswerSubmitDTO.builder().userAnswer(List.of("A")).build();
        assertThatThrownBy(() -> practiceService.submitAnswer(USER_ID, 999L, QUESTION_ID, dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }
}
