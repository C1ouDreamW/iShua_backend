package cn.heycloudream.ishua_backend.service.email;

import cn.heycloudream.ishua_backend.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
@Component
@RequiredArgsConstructor
public class ResendRegisterEmailSender implements RegisterEmailSender {

    private static final URI RESEND_EMAILS_URI = URI.create("https://api.resend.com/emails");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${RESEND_API_KEY:}")
    private String apiKey;

    @Value("${RESEND_FROM_EMAIL:}")
    private String fromEmail;

    @Override
    public void sendVerificationCode(String email, String code) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(500, "邮件服务未配置");
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new BusinessException(500, "邮件发件人未配置");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromEmail);
        payload.put("to", List.of(email));
        payload.put("subject", "iShua 注册验证码");
        payload.put("html", buildHtml(email, code));
        payload.put("text", buildText(code));

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(RESEND_EMAILS_URI)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Resend 邮件发送失败 status={} body={}", response.statusCode(), response.body());
                throw new BusinessException(500, "验证码邮件发送失败，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "验证码邮件发送失败，请稍后重试", e);
        } catch (IOException e) {
            throw new BusinessException(500, "验证码邮件发送失败，请稍后重试", e);
        }
    }

    private static String buildHtml(String email, String code) {
        return """
                <div style="font-family: Arial, sans-serif; line-height: 1.6;">
                  <p>你正在注册 iShua 账号。</p>
                  <p>验证码：<strong style="font-size: 24px; letter-spacing: 2px;">%s</strong></p>
                  <p>邮箱：%s</p>
                  <p>验证码 %d 分钟内有效，请勿泄露给他人。</p>
                </div>
                """.formatted(code, email, 10);
    }

    private static String buildText(String code) {
        return "你正在注册 iShua 账号，验证码是：" + code + "。10 分钟内有效，请勿泄露。";
    }
}
