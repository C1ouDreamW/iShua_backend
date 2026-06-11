package cn.heycloudream.ishua_backend.service.turnstile;

import cn.heycloudream.ishua_backend.common.constants.ValidationConstants;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.vo.turnstile.TurnstileSiteverifyResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudflareTurnstileVerificationService implements TurnstileVerificationService {

    private static final URI SITEVERIFY_URI =
            URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${TURNSTILE_SECRET_KEY:}")
    private String secretKey;

    @Value("${TURNSTILE_EXPECTED_HOSTNAME:}")
    private String expectedHostname;

    @Override
    public void verifyRegisterEmailCode(String token, String remoteIp) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new BusinessException(500, "人机验证服务未配置");
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(400, "请先完成人机验证");
        }
        if (token.length() > ValidationConstants.TURNSTILE_TOKEN_MAX_LENGTH) {
            throw new BusinessException(400, "人机验证无效，请重试");
        }

        TurnstileSiteverifyResponse response = callSiteverify(token, remoteIp);
        if (!response.isSuccess()) {
            log.warn("Turnstile 校验失败 errorCodes={} messages={}",
                    response.getErrorCodes(), response.getMessages());
            throw new BusinessException(400, "人机验证失败，请重试");
        }

        if (!ValidationConstants.TURNSTILE_ACTION_REGISTER_EMAIL_CODE.equals(response.getAction())) {
            log.warn("Turnstile action 不匹配 expected={} actual={}",
                    ValidationConstants.TURNSTILE_ACTION_REGISTER_EMAIL_CODE, response.getAction());
            throw new BusinessException(400, "人机验证失败，请重试");
        }

        if (expectedHostname != null && !expectedHostname.isBlank()
                && response.getHostname() != null
                && !expectedHostname.equalsIgnoreCase(response.getHostname())) {
            log.warn("Turnstile hostname 不匹配 expected={} actual={}",
                    expectedHostname, response.getHostname());
            throw new BusinessException(400, "人机验证失败，请重试");
        }
    }

    private TurnstileSiteverifyResponse callSiteverify(String token, String remoteIp) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("secret", secretKey);
        payload.put("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            payload.put("remoteip", remoteIp);
        }

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(SITEVERIFY_URI)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> httpResponse =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                log.warn("Turnstile Siteverify 请求失败 status={} body={}",
                        httpResponse.statusCode(), httpResponse.body());
                throw new BusinessException(500, "人机验证服务暂不可用，请稍后重试");
            }

            TurnstileSiteverifyResponse parsed =
                    objectMapper.readValue(httpResponse.body(), TurnstileSiteverifyResponse.class);
            if (parsed.getErrorCodes() == null) {
                parsed.setErrorCodes(List.of());
            }
            return parsed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "人机验证服务暂不可用，请稍后重试");
        } catch (IOException e) {
            log.warn("Turnstile Siteverify 调用异常", e);
            throw new BusinessException(500, "人机验证服务暂不可用，请稍后重试");
        }
    }
}
