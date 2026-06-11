package cn.heycloudream.ishua_backend.service;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.entity.BankNode;
import cn.heycloudream.ishua_backend.entity.Question;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.mapper.BankNodeMapper;
import cn.heycloudream.ishua_backend.mapper.QuestionMapper;
import cn.heycloudream.ishua_backend.service.impl.QuestionBankHotDetailServiceImpl;
import cn.heycloudream.ishua_backend.vo.questionbank.QuestionBankDetailBundleVO;
import cn.heycloudream.ishua_backend.vo.questionbank.QuestionBankVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QuestionBankHotDetailServiceImpl} Cache-Aside 核心路径单元测试。
 */
@ExtendWith(MockitoExtension.class)
class QuestionBankHotDetailServiceImplTest {

    private static final long BANK_ID = 1L;
    private static final String CACHE_KEY = IShuaRedisCacheConstants.bankDetailKey(BANK_ID);

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private BankNodeMapper bankNodeMapper;

    @Mock
    private QuestionMapper questionMapper;

    private QuestionBankHotDetailServiceImpl hotDetailService;

    @BeforeEach
    void initService() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        hotDetailService = new QuestionBankHotDetailServiceImpl(
                stringRedisTemplate, objectMapper, bankNodeMapper, questionMapper);
    }

    @Test
    @DisplayName("缓存命中：直接反序列化返回，不回源 MySQL")
    void getHotPublicBankDetail_cacheHit_shouldNotQueryDb() throws Exception {
        QuestionBankDetailBundleVO bundle = QuestionBankDetailBundleVO.builder()
                .bank(QuestionBankVO.builder().id(BANK_ID).title("缓存题库").build())
                .questions(List.of())
                .build();
        when(valueOperations.get(CACHE_KEY)).thenReturn(objectMapper.writeValueAsString(bundle));

        QuestionBankDetailBundleVO result = hotDetailService.getHotPublicBankDetail(BANK_ID);

        assertThat(result.getBank().getTitle()).isEqualTo("缓存题库");
        verify(bankNodeMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("NULL_BANK 占位：防穿透，抛 404")
    void getHotPublicBankDetail_nullPlaceholder_shouldThrow404() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(IShuaRedisCacheConstants.NULL_BANK_PLACEHOLDER);

        assertThatThrownBy(() -> hotDetailService.getHotPublicBankDetail(BANK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);
        verify(bankNodeMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("持锁回源：公开题库写入 Redis 并返回聚合数据")
    void getHotPublicBankDetail_cacheMiss_acquireLock_shouldLoadAndCache() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(stringRedisTemplate.execute(any(), any(), any())).thenReturn(1L);

        BankNode bank = publicBank();
        when(bankNodeMapper.selectById(BANK_ID)).thenReturn(bank);
        when(questionMapper.selectList(any())).thenReturn(Collections.emptyList());

        QuestionBankDetailBundleVO result = hotDetailService.getHotPublicBankDetail(BANK_ID);

        assertThat(result.getBank().getId()).isEqualTo(BANK_ID);
        verify(valueOperations).set(eq(CACHE_KEY), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("持锁回源：非公开题库 → 403")
    void getHotPublicBankDetail_privateBank_shouldThrow403() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(stringRedisTemplate.execute(any(), any(), any())).thenReturn(1L);

        BankNode bank = BankNode.builder()
                .id(BANK_ID)
                .userId(1L)
                .nodeKind("LEAF")
                .isPublic(0)
                .title("私有题库")
                .build();
        when(bankNodeMapper.selectById(BANK_ID)).thenReturn(bank);

        assertThatThrownBy(() -> hotDetailService.getHotPublicBankDetail(BANK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("持锁回源：题库不存在 → 写 NULL 占位并抛 404")
    void getHotPublicBankDetail_bankNotFound_shouldWriteNullPlaceholder() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(stringRedisTemplate.execute(any(), any(), any())).thenReturn(1L);
        when(bankNodeMapper.selectById(BANK_ID)).thenReturn(null);

        assertThatThrownBy(() -> hotDetailService.getHotPublicBankDetail(BANK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);

        verify(valueOperations).set(
                eq(CACHE_KEY),
                eq(IShuaRedisCacheConstants.NULL_BANK_PLACEHOLDER),
                any(Duration.class));
    }

    @Test
    @DisplayName("未获锁自旋：等待期间缓存写入后命中")
    void getHotPublicBankDetail_spinWait_shouldReturnWhenPeerFillsCache() throws Exception {
        QuestionBankDetailBundleVO bundle = QuestionBankDetailBundleVO.builder()
                .bank(QuestionBankVO.builder().id(BANK_ID).build())
                .questions(List.of())
                .build();
        String json = objectMapper.writeValueAsString(bundle);

        when(valueOperations.get(CACHE_KEY))
                .thenReturn(null)
                .thenReturn(json);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        QuestionBankDetailBundleVO result = hotDetailService.getHotPublicBankDetail(BANK_ID);

        assertThat(result.getBank().getId()).isEqualTo(BANK_ID);
        verify(bankNodeMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("自旋超时：直接回源 MySQL（不写缓存）")
    void getHotPublicBankDetail_spinTimeout_shouldFallbackToDb() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        BankNode bank = publicBank();
        when(bankNodeMapper.selectById(BANK_ID)).thenReturn(bank);
        when(questionMapper.selectList(any())).thenReturn(List.of(sampleQuestion()));

        QuestionBankDetailBundleVO result = hotDetailService.getHotPublicBankDetail(BANK_ID);

        assertThat(result.getQuestions()).hasSize(1);
        verify(valueOperations, never()).set(eq(CACHE_KEY), anyString(), any(Duration.class));
    }

    private static BankNode publicBank() {
        return BankNode.builder()
                .id(BANK_ID)
                .userId(1L)
                .nodeKind("LEAF")
                .title("公开题库")
                .isPublic(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }

    private static Question sampleQuestion() {
        return Question.builder()
                .id(10L)
                .questionBankId(BANK_ID)
                .questionType("SINGLE")
                .stem("示例题")
                .optionsJson("[\"A\",\"B\"]")
                .answerJson("[\"A\"]")
                .sortNo(1)
                .build();
    }
}
