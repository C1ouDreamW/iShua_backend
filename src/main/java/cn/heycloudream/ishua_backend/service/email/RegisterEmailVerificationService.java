package cn.heycloudream.ishua_backend.service.email;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.common.constants.ValidationConstants;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.service.turnstile.TurnstileVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterEmailVerificationService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RegisterEmailSender registerEmailSender;
    private final TurnstileVerificationService turnstileVerificationService;

    public void sendCode(String email, String turnstileToken, String clientIp) {
        turnstileVerificationService.verifyRegisterEmailCode(turnstileToken, clientIp);

        String normalizedEmail = normalizeEmail(email);
        String cooldownKey = IShuaRedisCacheConstants.registerEmailCodeCooldownKey(normalizedEmail);
        Boolean allowed = stringRedisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", Duration.ofSeconds(
                        ValidationConstants.AUTH_EMAIL_CODE_RESEND_COOLDOWN_SECONDS));
        if (!Boolean.TRUE.equals(allowed)) {
            throw new BusinessException(429, "验证码发送过于频繁，请稍后再试");
        }

        String code = generateCode();
        String codeKey = IShuaRedisCacheConstants.registerEmailCodeKey(normalizedEmail);
        String attemptsKey = IShuaRedisCacheConstants.registerEmailCodeAttemptsKey(normalizedEmail);

        stringRedisTemplate.opsForValue().set(
                codeKey,
                code,
                Duration.ofSeconds(ValidationConstants.AUTH_EMAIL_CODE_TTL_SECONDS));
        stringRedisTemplate.opsForValue().set(
                attemptsKey,
                "0",
                Duration.ofSeconds(ValidationConstants.AUTH_EMAIL_CODE_TTL_SECONDS));

        try {
            registerEmailSender.sendVerificationCode(normalizedEmail, code);
        } catch (RuntimeException ex) {
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(attemptsKey);
            stringRedisTemplate.delete(cooldownKey);
            throw ex;
        }
    }

    public void verifyCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String codeKey = IShuaRedisCacheConstants.registerEmailCodeKey(normalizedEmail);
        String attemptsKey = IShuaRedisCacheConstants.registerEmailCodeAttemptsKey(normalizedEmail);

        String stored = stringRedisTemplate.opsForValue().get(codeKey);
        if (stored == null || stored.isBlank()) {
            throw new BusinessException(400, "验证码已过期，请重新获取");
        }

        if (!stored.equals(code)) {
            Long attempts = stringRedisTemplate.opsForValue().increment(attemptsKey);
            if (attempts != null && attempts >= ValidationConstants.AUTH_EMAIL_CODE_MAX_ATTEMPTS) {
                clearCode(normalizedEmail);
                throw new BusinessException(429, "验证码错误次数过多，请重新获取");
            }
            throw new BusinessException(400, "验证码错误");
        }

        clearCode(normalizedEmail);
    }

    public String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void clearCode(String normalizedEmail) {
        stringRedisTemplate.delete(IShuaRedisCacheConstants.registerEmailCodeKey(normalizedEmail));
        stringRedisTemplate.delete(IShuaRedisCacheConstants.registerEmailCodeAttemptsKey(normalizedEmail));
    }

    private static String generateCode() {
        int code = ThreadLocalRandom.current().nextInt(0, 1_000_000);
        return String.format("%06d", code);
    }
}
