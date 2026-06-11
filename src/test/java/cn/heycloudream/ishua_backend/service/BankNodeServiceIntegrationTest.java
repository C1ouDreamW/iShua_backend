package cn.heycloudream.ishua_backend.service;

import cn.heycloudream.ishua_backend.dto.banknode.BankNodeCreateDTO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeMoveDTO;
import cn.heycloudream.ishua_backend.common.dto.PageRequestDTO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeSubtreeQueryDTO;
import cn.heycloudream.ishua_backend.dto.question.QuestionUpdateDTO;
import cn.heycloudream.ishua_backend.enums.UserRole;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.support.AbstractMockRedisSpringBootTest;
import cn.heycloudream.ishua_backend.support.UserContextTestSupport;
import cn.heycloudream.ishua_backend.vo.banknode.BankNodeVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BankNodeService} H2 集成测试：树 CRUD、防环、LEAF 挂题。
 */
class BankNodeServiceIntegrationTest extends AbstractMockRedisSpringBootTest {

    @Autowired
    private BankNodeService bankNodeService;

    @Autowired
    private QuestionService questionService;

    @Test
    @DisplayName("创建 FOLDER → LEAF → 录题 → 公开根节点可见")
    void folderLeafFlow_shouldWork() {
        UserContextTestSupport.setUser(1L, UserRole.PREMIUM);

        Long folderId = bankNodeService.createNode(1L, BankNodeCreateDTO.builder()
                .nodeKind("FOLDER")
                .title("集成测试课程")
                .build());
        Long leafId = bankNodeService.createNode(1L, BankNodeCreateDTO.builder()
                .parentId(folderId)
                .nodeKind("LEAF")
                .title("第一章")
                .isPublic(1)
                .build());

        QuestionUpdateDTO question = new QuestionUpdateDTO();
        question.setQuestionType("SINGLE");
        question.setStem("测试题干");
        question.setOptionsJson("[\"A\",\"B\"]");
        question.setAnswerJson("[\"A\"]");
        question.setSortNo(1);
        questionService.createQuestionInBank(1L, leafId, question);

        BankNodeVO folder = bankNodeService.getNode(1L, folderId);
        assertThat(folder.getChildCount()).isEqualTo(1);
        assertThat(folder.getDescendantLeafCount()).isGreaterThanOrEqualTo(1);

        PageRequestDTO rootsQuery = new PageRequestDTO(1, 20);
        List<BankNodeVO> publicRoots = bankNodeService.pagePublicRoots(rootsQuery).getRecords();
        assertThat(publicRoots.stream().anyMatch(n -> folderId.equals(n.getId()))).isTrue();

        BankNodeSubtreeQueryDTO treeQuery = BankNodeSubtreeQueryDTO.builder()
                .rootId(folderId)
                .build();
        List<BankNodeVO> subtree = bankNodeService.listPublicTree(treeQuery);
        assertThat(subtree).anyMatch(n -> leafId.equals(n.getId()) && n.getQuestionCount() == 1);
    }

    @Test
    @DisplayName("移动节点：同级重排应更新 sortNo 并反映顺序")
    void moveNode_reorderSiblings_shouldRenumber() {
        UserContextTestSupport.setUser(1L, UserRole.PREMIUM);

        Long folderId = bankNodeService.createNode(1L, BankNodeCreateDTO.builder()
                .nodeKind("FOLDER")
                .title("排序测试文件夹")
                .build());
        Long firstLeafId = bankNodeService.createNode(1L, BankNodeCreateDTO.builder()
                .parentId(folderId)
                .nodeKind("LEAF")
                .title("第一题")
                .build());
        Long secondLeafId = bankNodeService.createNode(1L, BankNodeCreateDTO.builder()
                .parentId(folderId)
                .nodeKind("LEAF")
                .title("第二题")
                .build());

        bankNodeService.moveNode(1L, firstLeafId, BankNodeMoveDTO.builder()
                .newParentId(folderId)
                .newSortNo(1)
                .build());

        BankNodeSubtreeQueryDTO treeQuery = BankNodeSubtreeQueryDTO.builder()
                .rootId(folderId)
                .build();
        List<BankNodeVO> subtree = bankNodeService.listMyTree(1L, treeQuery);
        List<BankNodeVO> children = subtree.stream()
                .filter(node -> folderId.equals(node.getParentId()))
                .sorted((left, right) -> Integer.compare(left.getSortNo(), right.getSortNo()))
                .toList();

        assertThat(children).hasSize(2);
        assertThat(children.get(0).getId()).isEqualTo(secondLeafId);
        assertThat(children.get(0).getSortNo()).isZero();
        assertThat(children.get(1).getId()).isEqualTo(firstLeafId);
        assertThat(children.get(1).getSortNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("移动节点：不能移到自身子树下")
    void moveNode_toDescendant_shouldReject() {
        UserContextTestSupport.setUser(1L, UserRole.PREMIUM);

        Long folderId = bankNodeService.createNode(1L, BankNodeCreateDTO.builder()
                .nodeKind("FOLDER")
                .title("父文件夹")
                .build());
        Long childId = bankNodeService.createNode(1L, BankNodeCreateDTO.builder()
                .parentId(folderId)
                .nodeKind("FOLDER")
                .title("子文件夹")
                .build());

        BankNodeMoveDTO move = BankNodeMoveDTO.builder()
                .newParentId(childId)
                .newSortNo(0)
                .build();
        assertThatThrownBy(() -> bankNodeService.moveNode(1L, folderId, move))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("FOLDER 节点禁止录题")
    void createQuestion_onFolder_shouldReject() {
        UserContextTestSupport.setUser(1L, UserRole.PREMIUM);

        Long folderId = bankNodeService.createNode(1L, BankNodeCreateDTO.builder()
                .nodeKind("FOLDER")
                .title("纯文件夹")
                .build());

        QuestionUpdateDTO question = new QuestionUpdateDTO();
        question.setQuestionType("SINGLE");
        question.setStem("题干");
        question.setOptionsJson("[\"A\"]");
        question.setAnswerJson("[\"A\"]");

        assertThatThrownBy(() -> questionService.createQuestionInBank(1L, folderId, question))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }
}
