package cn.heycloudream.ishua_backend.service.turnstile;

import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.vo.turnstile.TurnstileSiteverifyResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudflareTurnstileVerificationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = mock(HttpClient.class);
    private final CloudflareTurnstileVerificationService service =
            new CloudflareTurnstileVerificationService(objectMapper);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
        ReflectionTestUtils.setField(service, "secretKey", "test-secret");
        ReflectionTestUtils.setField(service, "expectedHostname", "");
    }

    @Test
    @DisplayName("verifyRegisterEmailCode: secret 未配置 → 500")
    void verifyRegisterEmailCode_secretMissing_shouldThrow500() {
        ReflectionTestUtils.setField(service, "secretKey", "");

        assertThatThrownBy(() -> service.verifyRegisterEmailCode("token", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(500);
    }

    @Test
    @DisplayName("verifyRegisterEmailCode: token 为空 → 400")
    void verifyRegisterEmailCode_tokenMissing_shouldThrow400() {
        assertThatThrownBy(() -> service.verifyRegisterEmailCode("", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("verifyRegisterEmailCode: Siteverify 返回失败 → 400")
    void verifyRegisterEmailCode_siteverifyFailed_shouldThrow400() throws Exception {
        stubSiteverifyResponse(false, null);

        assertThatThrownBy(() -> service.verifyRegisterEmailCode("token", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("verifyRegisterEmailCode: action 不匹配 → 400")
    void verifyRegisterEmailCode_actionMismatch_shouldThrow400() throws Exception {
        stubSiteverifyResponse(true, "wrong-action");

        assertThatThrownBy(() -> service.verifyRegisterEmailCode("token", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    private void stubSiteverifyResponse(boolean success, String action) throws Exception {
        TurnstileSiteverifyResponse body = new TurnstileSiteverifyResponse();
        body.setSuccess(success);
        body.setAction(action);

        @SuppressWarnings("unchecked")
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(body));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
    }
}
