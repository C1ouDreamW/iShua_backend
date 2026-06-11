package cn.heycloudream.ishua_backend.service.impl;

import cn.heycloudream.ishua_backend.common.dto.PageRequestDTO;
import cn.heycloudream.ishua_backend.common.vo.PageResultVO;
import cn.heycloudream.ishua_backend.dto.questionbank.QuestionBankCreateDTO;
import cn.heycloudream.ishua_backend.dto.questionbank.QuestionBankUpdateDTO;
import cn.heycloudream.ishua_backend.service.BankNodeService;
import cn.heycloudream.ishua_backend.service.QuestionBankService;
import cn.heycloudream.ishua_backend.vo.questionbank.QuestionBankVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 题库服务实现（兼容层）：委托 {@link BankNodeService} 处理 LEAF 节点。
 *
 * @author C1ouD
 */
@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl implements QuestionBankService {

    private final BankNodeService bankNodeService;

    @Override
    public PageResultVO<QuestionBankVO> pageMyBanks(Long currentUserId, PageRequestDTO page) {
        return bankNodeService.pageMyLeaves(currentUserId, page);
    }

    @Override
    public PageResultVO<QuestionBankVO> pagePublicBanks(PageRequestDTO page) {
        return bankNodeService.pagePublicLeaves(page);
    }

    @Override
    public Long createBank(Long currentUserId, QuestionBankCreateDTO dto) {
        return bankNodeService.createLeafAtRoot(currentUserId, dto);
    }

    @Override
    public void updateBank(Long currentUserId, Long bankId, QuestionBankUpdateDTO dto) {
        bankNodeService.updateLeaf(currentUserId, bankId, dto);
    }

    @Override
    public void deleteBank(Long currentUserId, Long bankId) {
        bankNodeService.deleteLeaf(currentUserId, bankId);
    }
}
