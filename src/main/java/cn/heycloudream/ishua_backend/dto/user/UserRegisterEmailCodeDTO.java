package cn.heycloudream.ishua_backend.dto.user;

import cn.heycloudream.ishua_backend.common.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Registration email code request.
 */
@Data
@Schema(description = "注册邮箱验证码请求")
public class UserRegisterEmailCodeDTO {

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = ValidationConstants.AUTH_EMAIL_MAX, message = "邮箱长度不能超过254")
    private String email;

    @Schema(description = "Cloudflare Turnstile 令牌", example = "0.abcdefghijklmnopqrstuvwxyz")
    @NotBlank(message = "请先完成人机验证")
    @Size(max = ValidationConstants.TURNSTILE_TOKEN_MAX_LENGTH, message = "人机验证令牌无效")
    private String turnstileToken;
}
