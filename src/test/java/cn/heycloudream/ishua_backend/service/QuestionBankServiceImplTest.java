package cn.heycloudream.ishua_backend.service;

import cn.heycloudream.ishua_backend.dto.questionbank.QuestionBankUpdateDTO;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.service.impl.QuestionBankServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * {@link QuestionBankServiceImpl} 兼容层单元测试。
 */
@ExtendWith(MockitoExtension.class)
class QuestionBankServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long BANK_ID = 10L;

    @Mock
    private BankNodeService bankNodeService;

    @InjectMocks
    private QuestionBankServiceImpl questionBankService;

    @Test
    @DisplayName("updateBank: 委托 BankNodeService.updateLeaf")
    void updateBank_success_shouldDelegate() {
        QuestionBankUpdateDTO dto = new QuestionBankUpdateDTO();
        dto.setTitle("新标题");
        dto.setDescription("描述");
        dto.setIsPublic(1);

        questionBankService.updateBank(USER_ID, BANK_ID, dto);

        verify(bankNodeService).updateLeaf(USER_ID, BANK_ID, dto);
    }

    @Test
    @DisplayName("deleteBank: 委托 BankNodeService.deleteLeaf")
    void deleteBank_success_shouldDelegate() {
        questionBankService.deleteBank(USER_ID, BANK_ID);
        verify(bankNodeService).deleteLeaf(USER_ID, BANK_ID);
    }

    @Test
    @DisplayName("updateBank: 底层异常向上传播")
    void updateBank_notOwner_shouldPropagate404() {
        QuestionBankUpdateDTO dto = new QuestionBankUpdateDTO();
        dto.setTitle("x");
        dto.setIsPublic(0);

        doThrow(new BusinessException(404, "节点不存在或无权访问"))
                .when(bankNodeService).updateLeaf(USER_ID, BANK_ID, dto);

        assertThatThrownBy(() -> questionBankService.updateBank(USER_ID, BANK_ID, dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);
    }
}
