package cn.heycloudream.ishua_test.controller;

import cn.heycloudream.ishua_backend.controller.BankNodeController;
import cn.heycloudream.ishua_backend.enums.UserRole;
import cn.heycloudream.ishua_backend.mapper.SysUserMapper;
import cn.heycloudream.ishua_backend.service.AiImportTaskService;
import cn.heycloudream.ishua_backend.service.BankNodeService;
import cn.heycloudream.ishua_backend.service.QuestionBankHotDetailService;
import cn.heycloudream.ishua_backend.service.QuestionService;
import cn.heycloudream.ishua_backend.service.ai.AiImportResultStore;
import cn.heycloudream.ishua_backend.service.ai.AiImportTaskMetaStore;
import cn.heycloudream.ishua_backend.service.ai.AiImportTaskStatusStore;
import cn.heycloudream.ishua_backend.service.ai.ImportIdempotentService;
import cn.heycloudream.ishua_backend.support.AtlasWebMvcTestConfig;
import cn.heycloudream.ishua_backend.support.MockMvcTestSupport;
import cn.heycloudream.ishua_backend.support.WebMvcAuthTestSupport;
import cn.heycloudream.ishua_backend.vo.banknode.BankNodeVO;
import cn.heycloudream.ishua_test.IShuaTestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link BankNodeController} 公开/我的树路径鉴权 Web 切片测试。
 */
@WebMvcTest(controllers = BankNodeController.class)
@ContextConfiguration(classes = {IShuaTestApplication.class, BankNodeController.class})
@Import(AtlasWebMvcTestConfig.class)
@ActiveProfiles("test")
class BankNodeControllerTest {

    private static final Long PREMIUM_USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BankNodeService bankNodeService;

    @MockBean
    private QuestionService questionService;

    @MockBean
    private QuestionBankHotDetailService questionBankHotDetailService;

    @MockBean
    private ImportIdempotentService importIdempotentService;

    @MockBean
    private AiImportTaskStatusStore taskStatusStore;

    @MockBean
    private AiImportResultStore resultStore;

    @MockBean
    private AiImportTaskMetaStore taskMetaStore;

    @MockBean
    private AiImportTaskService aiImportTaskService;

    @MockBean
    private SysUserMapper sysUserMapper;

    @BeforeEach
    void setUp() {
        when(bankNodeService.listPublicTree(any())).thenReturn(List.of());
        when(bankNodeService.listMyTree(eq(PREMIUM_USER_ID), any())).thenReturn(List.of(
                BankNodeVO.builder().id(10L).title("我的题库").build()
        ));
    }

    @Test
    @DisplayName("GET /public/tree 无 token → 200")
    void listPublicTree_withoutToken_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/bank-nodes/public/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(bankNodeService).listPublicTree(any());
    }

    @Test
    @DisplayName("GET /mine/tree 无 token → 401")
    void listMyTree_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/bank-nodes/mine/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("GET /mine/tree USER token → 403")
    void listMyTree_basicUser_shouldReturn403() throws Exception {
        WebMvcAuthTestSupport.stubBasicUser(sysUserMapper, PREMIUM_USER_ID);

        mockMvc.perform(MockMvcTestSupport.withBearerAuth(
                        get("/api/v1/bank-nodes/mine/tree"), PREMIUM_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("GET /mine/tree PREMIUM token → 200")
    void listMyTree_premiumUser_shouldReturn200() throws Exception {
        WebMvcAuthTestSupport.stubUser(sysUserMapper, PREMIUM_USER_ID, UserRole.PREMIUM);

        mockMvc.perform(MockMvcTestSupport.withBearerAuth(
                        get("/api/v1/bank-nodes/mine/tree"), PREMIUM_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].title").value("我的题库"));

        verify(bankNodeService).listMyTree(eq(PREMIUM_USER_ID), any());
    }

    @Test
    @DisplayName("GET /mine/tree ADMIN token → 200")
    void listMyTree_adminUser_shouldReturn200() throws Exception {
        WebMvcAuthTestSupport.stubUser(sysUserMapper, PREMIUM_USER_ID, UserRole.ADMIN);

        mockMvc.perform(MockMvcTestSupport.withBearerAuth(
                        get("/api/v1/bank-nodes/mine/tree"), PREMIUM_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
