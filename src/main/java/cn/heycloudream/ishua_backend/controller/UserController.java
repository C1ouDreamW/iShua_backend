package cn.heycloudream.ishua_backend.controller;

import cn.heycloudream.ishua_backend.common.vo.Result;
import cn.heycloudream.ishua_backend.config.OpenApiConfig;
import cn.heycloudream.ishua_backend.config.openapi.ApiDocPublicEndpoint;
import cn.heycloudream.ishua_backend.config.openapi.ApiDocStandardResponses;
import cn.heycloudream.ishua_backend.dto.user.UserLoginDTO;
import cn.heycloudream.ishua_backend.dto.user.UserRegisterDTO;
import cn.heycloudream.ishua_backend.dto.user.UserRegisterEmailCodeDTO;
import cn.heycloudream.ishua_backend.service.UserService;
import cn.heycloudream.ishua_backend.service.email.RegisterEmailVerificationService;
import cn.heycloudream.ishua_backend.util.ClientIpUtils;
import cn.heycloudream.ishua_backend.util.UserContextHolder;
import cn.heycloudream.ishua_backend.vo.user.UserLoginVO;
import cn.heycloudream.ishua_backend.vo.user.UserMeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User auth endpoints.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "用户鉴权", description = "注册与登录无需 JWT；`GET /me` 须 JWT，最低角色 USER")
@ApiDocStandardResponses
public class UserController {

    private final UserService userService;
    private final RegisterEmailVerificationService registerEmailVerificationService;

    @PostMapping("/register/email-code")
    @ApiDocPublicEndpoint
    @Operation(
            summary = "发送注册邮箱验证码",
            description = "无需登录。须先通过 Cloudflare Turnstile 人机验证，再向指定邮箱发送 6 位验证码。"
    )
    public Result<Void> sendRegisterEmailCode(
            @Valid @RequestBody UserRegisterEmailCodeDTO dto,
            HttpServletRequest request) {
        registerEmailVerificationService.sendCode(
                dto.getEmail(),
                dto.getTurnstileToken(),
                ClientIpUtils.resolveClientIp(request));
        return Result.success(null);
    }

    @PostMapping("/register")
    @ApiDocPublicEndpoint
    @Operation(
            summary = "用户注册",
            description = "无需登录。先校验邮箱验证码，通过后再创建用户。"
    )
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO dto) {
        userService.register(dto);
        return Result.success(null);
    }

    @PostMapping("/login")
    @ApiDocPublicEndpoint
    @Operation(
            summary = "用户登录",
            description = "无需登录。校验账号密码，成功返回 JWT 与用户信息。"
    )
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(
            summary = "获取当前登录用户信息",
            description = "须 JWT，最低角色 USER。"
    )
    public Result<UserMeVO> me() {
        return Result.success(userService.getCurrentUser(UserContextHolder.get()));
    }
}
