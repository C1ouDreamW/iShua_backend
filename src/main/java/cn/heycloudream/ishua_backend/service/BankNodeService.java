package cn.heycloudream.ishua_backend.service;

import cn.heycloudream.ishua_backend.common.dto.PageRequestDTO;
import cn.heycloudream.ishua_backend.common.vo.PageResultVO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeCreateDTO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeMoveDTO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeSubtreeQueryDTO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeUpdateDTO;
import cn.heycloudream.ishua_backend.dto.questionbank.QuestionBankCreateDTO;
import cn.heycloudream.ishua_backend.dto.questionbank.QuestionBankUpdateDTO;
import cn.heycloudream.ishua_backend.entity.BankNode;
import cn.heycloudream.ishua_backend.vo.banknode.BankNodeVO;
import cn.heycloudream.ishua_backend.vo.questionbank.QuestionBankVO;

import java.util.List;

/**
 * 题库树节点领域服务。
 *
 * @author C1ouD
 */
public interface BankNodeService {

    Long createNode(Long currentUserId, BankNodeCreateDTO dto);

    void updateNode(Long currentUserId, Long nodeId, BankNodeUpdateDTO dto);

    void deleteNode(Long currentUserId, Long nodeId);

    void moveNode(Long currentUserId, Long nodeId, BankNodeMoveDTO dto);

    BankNodeVO getNode(Long currentUserId, Long nodeId);

    PageResultVO<BankNodeVO> pagePublicRoots(PageRequestDTO query);

    List<BankNodeVO> listPublicTree(BankNodeSubtreeQueryDTO query);

    PageResultVO<BankNodeVO> pageMyRoots(Long currentUserId, PageRequestDTO query);

    List<BankNodeVO> listMyTree(Long currentUserId, BankNodeSubtreeQueryDTO query);

    PageResultVO<QuestionBankVO> pageMyLeaves(Long currentUserId, PageRequestDTO page);

    PageResultVO<QuestionBankVO> pagePublicLeaves(PageRequestDTO page);

    Long createLeafAtRoot(Long currentUserId, QuestionBankCreateDTO dto);

    void updateLeaf(Long currentUserId, Long leafId, QuestionBankUpdateDTO dto);

    void deleteLeaf(Long currentUserId, Long leafId);

    void adjustQuestionCount(Long leafId, int delta);

    void resetQuestionCount(Long leafId, int count);

    BankNode requireExistsNode(Long nodeId);
}
