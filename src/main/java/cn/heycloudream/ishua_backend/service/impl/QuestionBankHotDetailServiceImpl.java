package cn.heycloudream.ishua_backend.service.impl;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.entity.Question;
import cn.heycloudream.ishua_backend.entity.BankNode;
import cn.heycloudream.ishua_backend.enums.BankNodeKind;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.mapper.BankNodeMapper;
import cn.heycloudream.ishua_backend.mapper.QuestionMapper;
import cn.heycloudream.ishua_backend.service.QuestionBankHotDetailService;
import cn.heycloudream.ishua_backend.vo.question.QuestionVO;
import cn.heycloudream.ishua_backend.vo.questionbank.QuestionBankDetailBundleVO;
import cn.heycloudream.ishua_backend.vo.questionbank.QuestionBankVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 热点题库详情：StringRedisTemplate 存 JSON、随机 TTL 防雪崩、SET NX EX 防击穿、NULL_BANK 防穿透。
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionBankHotDetailServiceImpl implements QuestionBankHotDetailService {

    private static final int SPIN_MAX_ATTEMPTS = 40;
    private static final long SPIN_SLEEP_MS = 50L;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>();

    static {
        UNLOCK_SCRIPT.setResultType(Long.class);
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final BankNodeMapper bankNodeMapper;
    private final QuestionMapper questionMapper;

    @Override
    public QuestionBankDetailBundleVO getHotPublicBankDetail(long bankId) {
        String cacheKey = IShuaRedisCacheConstants.bankDetailKey(bankId);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        QuestionBankDetailBundleVO fromCache = resolveCachedValue(cacheKey, cached);
        if (fromCache != null) {
            return fromCache;
        }
        if (IShuaRedisCacheConstants.NULL_BANK_PLACEHOLDER.equals(cached)) {
            throw new BusinessException(404, "题库不存在");
        }

        String lockKey = IShuaRedisCacheConstants.bankDetailLockKey(bankId);
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, Duration.ofSeconds(IShuaRedisCacheConstants.BANK_DETAIL_LOCK_TTL_SECONDS)));

        if (Boolean.TRUE.equals(locked)) {
            try {
                return loadThroughLock(cacheKey, bankId);
            } finally {
                unlockSafely(lockKey, lockToken);
            }
        }

        for (int i = 0; i < SPIN_MAX_ATTEMPTS; i++) {
            sleepQuietly();
            String again = stringRedisTemplate.opsForValue().get(cacheKey);
            QuestionBankDetailBundleVO hit = resolveCachedValue(cacheKey, again);
            if (hit != null) {
                return hit;
            }
            if (IShuaRedisCacheConstants.NULL_BANK_PLACEHOLDER.equals(again)) {
                throw new BusinessException(404, "题库不存在");
            }
        }

        log.warn("热点题库缓存自旋等待超时，直接回源 MySQL, bankId={}", bankId);
        return loadPublicBundleFromDbWithoutWrite(bankId);
    }

    /**
     * 持锁线程：双重检查 → 回源 → 写 Redis（正文随机 TTL / 空值短 TTL）。
     */
    private QuestionBankDetailBundleVO loadThroughLock(String cacheKey, long bankId) {
        String second = stringRedisTemplate.opsForValue().get(cacheKey);
        QuestionBankDetailBundleVO hit = resolveCachedValue(cacheKey, second);
        if (hit != null) {
            return hit;
        }
        if (IShuaRedisCacheConstants.NULL_BANK_PLACEHOLDER.equals(second)) {
            throw new BusinessException(404, "题库不存在");
        }

        BankNode bank = requirePublicLeaf(bankId);
        if (bank == null) {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    IShuaRedisCacheConstants.NULL_BANK_PLACEHOLDER,
                    Duration.ofSeconds(IShuaRedisCacheConstants.NULL_BANK_CACHE_TTL_SECONDS));
            throw new BusinessException(404, "题库不存在");
        }

        QuestionBankDetailBundleVO bundle = buildBundle(bank);
        try {
            String json = objectMapper.writeValueAsString(bundle);
            int ttlSeconds = ThreadLocalRandom.current().nextInt(
                    IShuaRedisCacheConstants.BANK_DETAIL_CACHE_TTL_MIN_SECONDS,
                    IShuaRedisCacheConstants.BANK_DETAIL_CACHE_TTL_MAX_SECONDS + 1);
            stringRedisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException e) {
            log.error("热点题库详情序列化失败, bankId={}", bankId, e);
            throw new BusinessException(500, "缓存序列化失败");
        }
        return bundle;
    }

    /**
     * 自旋超时后的兜底：不再写缓存，避免多线程同时回源放大。
     */
    private QuestionBankDetailBundleVO loadPublicBundleFromDbWithoutWrite(long bankId) {
        BankNode bank = requirePublicLeaf(bankId);
        return buildBundle(bank);
    }

    private BankNode requirePublicLeaf(long bankId) {
        BankNode bank = bankNodeMapper.selectById(bankId);
        if (bank == null) {
            return null;
        }
        if (!BankNodeKind.LEAF.name().equals(bank.getNodeKind())) {
            throw new BusinessException(400, "仅题库节点支持热点刷题缓存");
        }
        if (bank.getIsPublic() == null || bank.getIsPublic() != 1) {
            throw new BusinessException(403, "仅公开热点题库支持聚合刷题缓存接口");
        }
        return bank;
    }

    private QuestionBankDetailBundleVO buildBundle(BankNode bank) {
        List<Question> questions = questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getQuestionBankId, bank.getId())
                        .orderByAsc(Question::getSortNo)
                        .orderByDesc(Question::getUpdateTime));
        List<QuestionVO> vos = questions.stream().map(this::toQuestionVo).collect(Collectors.toList());
        return QuestionBankDetailBundleVO.builder()
                .bank(toBankVo(bank))
                .questions(vos)
                .build();
    }

    /**
     * @param cacheKey 缓存主 Key（反序列化失败时用于删除脏数据）
     * @param raw      Redis 返回值
     * @return 命中正文时返回对象；未命中或空值占位返回 {@code null}（空值占位由调用方根据 raw 判断）
     */
    private QuestionBankDetailBundleVO resolveCachedValue(String cacheKey, String raw) {
        if (!StringUtils.hasText(raw) || IShuaRedisCacheConstants.NULL_BANK_PLACEHOLDER.equals(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, QuestionBankDetailBundleVO.class);
        } catch (JsonProcessingException e) {
            log.warn("热点题库缓存 JSON 反序列化失败，将删除脏数据, rawPrefix={}",
                    raw.length() > 64 ? raw.substring(0, 64) : raw, e);
            stringRedisTemplate.delete(cacheKey);
            return null;
        }
    }

    private void unlockSafely(String lockKey, String token) {
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), token);
        } catch (Exception e) {
            log.warn("释放热点题库重建锁失败, lockKey={}", lockKey, e);
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(SPIN_SLEEP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "请求被中断");
        }
    }

    private QuestionBankVO toBankVo(BankNode e) {
        return QuestionBankVO.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .title(e.getTitle())
                .description(e.getDescription())
                .isPublic(e.getIsPublic())
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }

    private QuestionVO toQuestionVo(Question e) {
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
