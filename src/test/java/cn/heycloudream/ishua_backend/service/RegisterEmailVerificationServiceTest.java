package cn.heycloudream.ishua_backend.service;

import cn.heycloudream.ishua_backend.common.constants.ValidationConstants;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.service.email.RegisterEmailSender;
import cn.heycloudream.ishua_backend.service.email.RegisterEmailVerificationService;
import cn.heycloudream.ishua_backend.service.turnstile.TurnstileVerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterEmailVerificationServiceTest {

    private static final String TURNSTILE_TOKEN = "turnstile-token";
    private static final String CLIENT_IP = "203.0.113.1";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RegisterEmailSender registerEmailSender;

    @Mock
    private TurnstileVerificationService turnstileVerificationService;

    @InjectMocks
    private RegisterEmailVerificationService service;

    @Test
    @DisplayName("sendCode: Turnstile 校验失败 → 不发邮件")
    void sendCode_turnstileFailed_shouldNotSendEmail() {
        doThrow(new BusinessException(400, "人机验证失败，请重试"))
                .when(turnstileVerificationService)
                .verifyRegisterEmailCode(TURNSTILE_TOKEN, CLIENT_IP);

        assertThatThrownBy(() -> service.sendCode("user@example.com", TURNSTILE_TOKEN, CLIENT_IP))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);

        verify(registerEmailSender, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    @DisplayName("sendCode: 冷却期内重复发送 → 429")
    void sendCode_withinCooldown_shouldThrow429() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service.sendCode("user@example.com", TURNSTILE_TOKEN, CLIENT_IP))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(429);

        verify(turnstileVerificationService).verifyRegisterEmailCode(TURNSTILE_TOKEN, CLIENT_IP);
        verify(registerEmailSender, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    @DisplayName("sendCode: 成功 → 写入 Redis 并发送邮件")
    void sendCode_success_shouldStoreCodeAndSendEmail() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        service.sendCode("User@Example.com", TURNSTILE_TOKEN, CLIENT_IP);

        verify(turnstileVerificationService).verifyRegisterEmailCode(TURNSTILE_TOKEN, CLIENT_IP);
        verify(valueOperations, times(2)).set(
                anyString(),
                anyString(),
                eq(Duration.ofSeconds(ValidationConstants.AUTH_EMAIL_CODE_TTL_SECONDS)));
        verify(registerEmailSender).sendVerificationCode(eq("user@example.com"), anyString());
    }

    @Test
    @DisplayName("verifyCode: 验证码过期 → 400")
    void verifyCode_expired_shouldThrow400() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.verifyCode("user@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("verifyCode: 验证码错误 → 400")
    void verifyCode_wrong_shouldThrow400() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("654321");
        when(valueOperations.increment(anyString())).thenReturn(1L);

        assertThatThrownBy(() -> service.verifyCode("user@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("verifyCode: 错误次数过多 → 429 并清理验证码")
    void verifyCode_tooManyAttempts_shouldThrow429AndClearCode() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("654321");
        when(valueOperations.increment(anyString())).thenReturn(5L);

        assertThatThrownBy(() -> service.verifyCode("user@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(429);

        verify(stringRedisTemplate, times(2)).delete(anyString());
    }

    @Test
    @DisplayName("verifyCode: 成功 → 清理验证码")
    void verifyCode_success_shouldClearCode() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("123456");

        service.verifyCode("user@example.com", "123456");

        verify(stringRedisTemplate, times(2)).delete(anyString());
    }

    @Test
    @DisplayName("normalizeEmail: 去空格并转小写")
    void normalizeEmail_shouldTrimAndLowerCase() {
        assertThat(service.normalizeEmail("  User@Example.COM ")).isEqualTo("user@example.com");
    }
}
