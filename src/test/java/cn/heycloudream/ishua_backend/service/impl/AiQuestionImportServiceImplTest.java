package cn.heycloudream.ishua_backend.service.impl;

import cn.heycloudream.ishua_backend.enums.AiImportTaskStatus;
import cn.heycloudream.ishua_backend.service.AiImportTaskService;
import cn.heycloudream.ishua_backend.service.ai.AiImportTaskMetaStore;
import cn.heycloudream.ishua_backend.service.ai.AiImportTaskStatusStore;
import cn.heycloudream.ishua_backend.service.file.FileStorageService;
import cn.heycloudream.ishua_backend.service.guard.BankAccessGuard;
import cn.heycloudream.ishua_backend.vo.ai.AiImportSubmitVO;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskMetaVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQuestionImportServiceImplTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private StreamOperations<String, Object, String> streamOperations;

    @Mock
    private AiImportTaskStatusStore taskStatusStore;

    @Mock
    private AiImportTaskMetaStore taskMetaStore;

    @Mock
    private AiImportTaskService aiImportTaskService;

    @Mock
    private BankAccessGuard bankAccessGuard;

    @InjectMocks
    private AiQuestionImportServiceImpl service;

    @Test
    @SuppressWarnings("unchecked")
    void submitFileImport_shouldSubmitTaskAndPublishStream() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.pdf",
                "application/pdf",
                "pdf-content".getBytes()
        );

        when(fileStorageService.store(file)).thenReturn("file:///tmp/demo.pdf");
        when(stringRedisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"taskId\":\"mock\"}");

        AiImportSubmitVO result = service.submitFileImport(1L, 10L, file);

        assertThat(result.getStatus()).isEqualTo(AiImportTaskStatus.SUBMITTED.name());
        assertThat(result.getTaskId()).isNotNull();

        // 验证 Redis Stream opsForStream 被调用（add + trim，各一次）
        verify(stringRedisTemplate, times(2)).opsForStream();

        // 验证元数据写入
        ArgumentCaptor<AiImportTaskMetaVO> metaCaptor = ArgumentCaptor.forClass(AiImportTaskMetaVO.class);
        verify(taskMetaStore).write(eq(result.getTaskId()), metaCaptor.capture());

        AiImportTaskMetaVO meta = metaCaptor.getValue();
        assertThat(meta.getTaskId()).isEqualTo(result.getTaskId());
        assertThat(meta.getUserId()).isEqualTo(1L);
        assertThat(meta.getBankId()).isEqualTo(10L);
        assertThat(meta.getFileName()).isEqualTo("demo.pdf");
        assertThat(meta.getFileUrl()).isEqualTo("file:///tmp/demo.pdf");
        assertThat(meta.getFileSize()).isEqualTo(file.getSize());
        assertThat(meta.getType()).isEqualTo("file");
        assertThat(meta.getSubmittedAt()).isPositive();

        verify(aiImportTaskService).createOnSubmit(any());
        verify(taskStatusStore).write(result.getTaskId(), AiImportTaskStatus.SUBMITTED, null, null);
    }
}
