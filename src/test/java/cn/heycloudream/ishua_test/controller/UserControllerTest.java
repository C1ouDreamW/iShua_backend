package cn.heycloudream.ishua_test.controller;

import cn.heycloudream.ishua_backend.controller.UserController;
import cn.heycloudream.ishua_test.IShuaTestApplication;
import cn.heycloudream.ishua_backend.dto.user.UserLoginDTO;
import cn.heycloudream.ishua_backend.dto.user.UserRegisterDTO;
import cn.heycloudream.ishua_backend.dto.user.UserRegisterEmailCodeDTO;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.mapper.SysUserMapper;
import cn.heycloudream.ishua_backend.service.UserService;
import cn.heycloudream.ishua_backend.service.email.RegisterEmailVerificationService;
import cn.heycloudream.ishua_backend.support.AtlasWebMvcTestConfig;
import cn.heycloudream.ishua_backend.support.MockMvcTestSupport;
import cn.heycloudream.ishua_backend.support.WebMvcAuthTestSupport;
import cn.heycloudream.ishua_backend.vo.user.UserLoginVO;
import cn.heycloudream.ishua_backend.vo.user.UserMeVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link UserController} Web 切片测试。
 */
@WebMvcTest(controllers = UserController.class)
@ContextConfiguration(classes = {IShuaTestApplication.class, UserController.class})
@Import(AtlasWebMvcTestConfig.class)
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private SysUserMapper sysUserMapper;

    @MockBean
    private RegisterEmailVerificationService registerEmailVerificationService;

    @Test
    @DisplayName("POST /register/email-code: 参数校验失败 → code=400")
    void sendRegisterEmailCode_validationFail_shouldReturn400() throws Exception {
        UserRegisterEmailCodeDTO dto = new UserRegisterEmailCodeDTO();
        dto.setEmail("invalid-email");

        mockMvc.perform(post("/api/v1/users/register/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /register/email-code: 成功 → code=200")
    void sendRegisterEmailCode_success_shouldReturn200() throws Exception {
        UserRegisterEmailCodeDTO dto = buildValidEmailCodeDto();

        mockMvc.perform(post("/api/v1/users/register/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(registerEmailVerificationService).sendCode(
                eq("user@example.com"),
                eq("turnstile-token"),
                any());
    }

    @Test
    @DisplayName("POST /register/email-code: 人机验证失败 → code=400")
    void sendRegisterEmailCode_turnstileFailed_shouldReturn400() throws Exception {
        UserRegisterEmailCodeDTO dto = buildValidEmailCodeDto();

        doThrow(new BusinessException(400, "人机验证失败，请重试"))
                .when(registerEmailVerificationService)
                .sendCode(eq("user@example.com"), eq("turnstile-token"), any());

        mockMvc.perform(post("/api/v1/users/register/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /register/email-code: 频控 → code=429")
    void sendRegisterEmailCode_rateLimited_shouldReturn429() throws Exception {
        UserRegisterEmailCodeDTO dto = buildValidEmailCodeDto();

        doThrow(new BusinessException(429, "验证码发送过于频繁，请稍后再试"))
                .when(registerEmailVerificationService)
                .sendCode(eq("user@example.com"), eq("turnstile-token"), any());

        mockMvc.perform(post("/api/v1/users/register/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(429));
    }

    @Test
    @DisplayName("POST /register: 参数校验失败 → code=400")
    void register_validationFail_shouldReturn400() throws Exception {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("ab");
        dto.setPassword("123");

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /register: 成功 → code=200")
    void register_success_shouldReturn200() throws Exception {
        UserRegisterDTO dto = buildValidRegisterDto();

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userService).register(any(UserRegisterDTO.class));
    }

    @Test
    @DisplayName("POST /register: 验证码错误 → code=400")
    void register_invalidCode_shouldReturn400() throws Exception {
        UserRegisterDTO dto = buildValidRegisterDto();

        doThrow(new BusinessException(400, "验证码错误")).when(userService).register(any());

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /login: 账号或密码错误 → code=401")
    void login_invalidCredentials_shouldReturn401() throws Exception {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUsername("nobody");
        dto.setPassword("wrong");

        doThrow(new BusinessException(401, "账号或密码错误")).when(userService).login(any());

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("POST /login: 成功 → code=200 且返回 token")
    void login_success_shouldReturnToken() throws Exception {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUsername("alice");
        dto.setPassword("123456");

        when(userService.login(any())).thenReturn(UserLoginVO.builder()
                .token("jwt")
                .userId(1L)
                .username("alice")
                .role("USER")
                .build());

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("jwt"));
    }

    @Test
    @DisplayName("GET /me: 无 Token → code=401")
    void me_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("GET /me: 有效 Token → code=200")
    void me_withToken_shouldReturn200() throws Exception {
        WebMvcAuthTestSupport.stubBasicUser(sysUserMapper, 1L);
        when(userService.getCurrentUser(1L)).thenReturn(UserMeVO.builder()
                .userId(1L)
                .username("testuser")
                .role("USER")
                .build());

        mockMvc.perform(MockMvcTestSupport.withBearerAuth(get("/api/v1/users/me"), 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(1));
    }

    private static UserRegisterEmailCodeDTO buildValidEmailCodeDto() {
        UserRegisterEmailCodeDTO dto = new UserRegisterEmailCodeDTO();
        dto.setEmail("user@example.com");
        dto.setTurnstileToken("turnstile-token");
        return dto;
    }

    private static UserRegisterDTO buildValidRegisterDto() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword("123456");
        dto.setEmail("newuser@example.com");
        dto.setCode("123456");
        return dto;
    }
}
