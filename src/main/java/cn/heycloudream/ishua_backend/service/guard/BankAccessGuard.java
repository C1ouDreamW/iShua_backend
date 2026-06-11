package cn.heycloudream.ishua_backend.service.guard;

import cn.heycloudream.ishua_backend.entity.BankNode;
import cn.heycloudream.ishua_backend.enums.BankNodeKind;
import cn.heycloudream.ishua_backend.enums.UserRole;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.mapper.BankNodeMapper;
import cn.heycloudream.ishua_backend.util.UserContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 题库树节点访问守卫，统一处理归属校验、叶子节点能力与管理员运维放行。
 *
 * @author C1ouD
 */
@Component
@RequiredArgsConstructor
public class BankAccessGuard {

    private final BankNodeMapper bankNodeMapper;

    public BankNode requireOwnedNode(Long currentUserId, Long nodeId) {
        return requireOwnedNode(currentUserId, UserContextHolder.getRole(), nodeId);
    }

    public BankNode requireOwnedNode(Long currentUserId, UserRole role, Long nodeId) {
        BankNode node = bankNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new BusinessException(404, "节点不存在或无权访问");
        }
        if (UserRole.ADMIN.equals(role)) {
            return node;
        }
        if (currentUserId == null || node.getUserId() == null || !node.getUserId().equals(currentUserId)) {
            throw new BusinessException(404, "节点不存在或无权访问");
        }
        return node;
    }

    /**
     * 兼容旧命名：要求当前用户拥有指定 LEAF 题库节点。
     */
    public BankNode requireOwnedBank(Long currentUserId, Long bankId) {
        return requireOwnedLeaf(currentUserId, bankId);
    }

    public BankNode requireOwnedBank(Long currentUserId, UserRole role, Long bankId) {
        return requireOwnedLeaf(currentUserId, role, bankId);
    }

    public BankNode requireOwnedLeaf(Long currentUserId, Long nodeId) {
        BankNode node = requireOwnedNode(currentUserId, nodeId);
        requireLeaf(node);
        return node;
    }

    public BankNode requireOwnedLeaf(Long currentUserId, UserRole role, Long nodeId) {
        BankNode node = requireOwnedNode(currentUserId, role, nodeId);
        requireLeaf(node);
        return node;
    }

    public void requireLeaf(BankNode node) {
        if (node == null || !BankNodeKind.LEAF.name().equals(node.getNodeKind())) {
            throw new BusinessException(400, "仅题库节点可执行该操作");
        }
    }

    public void requireFolder(BankNode node) {
        if (node == null || !BankNodeKind.FOLDER.name().equals(node.getNodeKind())) {
            throw new BusinessException(400, "父节点须为文件夹");
        }
    }

    /**
     * 私有 LEAF 刷题入口：普通用户不可访问私有库，高级用户须拥有该节点，管理员可放行。
     */
    public void requirePrivatePracticeAccess(Long currentUserId, UserRole role, BankNode node) {
        if (node == null) {
            throw new BusinessException(404, "节点不存在");
        }
        requireLeaf(node);
        if (UserRole.ADMIN.equals(role)) {
            return;
        }
        if (role == null || !role.includes(UserRole.PREMIUM)) {
            throw new BusinessException(403, "无权访问该题库");
        }
        if (currentUserId == null || node.getUserId() == null || !node.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "无权访问该题库");
        }
    }
}
