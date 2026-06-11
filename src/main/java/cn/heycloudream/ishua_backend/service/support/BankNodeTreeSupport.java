package cn.heycloudream.ishua_backend.service.support;

import cn.heycloudream.ishua_backend.entity.BankNode;
import cn.heycloudream.ishua_backend.enums.BankNodeKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 题库树内存计算辅助：子树收集、公开可见性、统计字段。
 *
 * @author C1ouD
 */
public final class BankNodeTreeSupport {

    private BankNodeTreeSupport() {
    }

    public static Map<Long, List<BankNode>> groupByParent(List<BankNode> nodes) {
        Map<Long, List<BankNode>> grouped = new HashMap<>();
        for (BankNode node : nodes) {
            Long parentKey = node.getParentId();
            grouped.computeIfAbsent(parentKey, ignored -> new ArrayList<>()).add(node);
        }
        for (List<BankNode> siblings : grouped.values()) {
            siblings.sort((a, b) -> {
                int sortCompare = Integer.compare(
                        a.getSortNo() == null ? 0 : a.getSortNo(),
                        b.getSortNo() == null ? 0 : b.getSortNo());
                if (sortCompare != 0) {
                    return sortCompare;
                }
                return Long.compare(a.getId(), b.getId());
            });
        }
        return grouped;
    }

    public static Set<Long> collectSubtreeIds(Long rootId, Map<Long, List<BankNode>> childrenByParent) {
        Set<Long> ids = new HashSet<>();
        Queue<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!ids.add(current)) {
                continue;
            }
            for (BankNode child : childrenByParent.getOrDefault(current, List.of())) {
                queue.add(child.getId());
            }
        }
        return ids;
    }

    public static boolean isDescendant(Long ancestorId, Long nodeId, Map<Long, BankNode> byId) {
        Long currentParent = nodeId;
        while (currentParent != null) {
            BankNode current = byId.get(currentParent);
            if (current == null) {
                return false;
            }
            if (ancestorId.equals(current.getId())) {
                return true;
            }
            currentParent = current.getParentId();
        }
        return false;
    }

    public static boolean isPublicLeaf(BankNode node) {
        return BankNodeKind.LEAF.name().equals(node.getNodeKind())
                && node.getIsPublic() != null
                && node.getIsPublic() == 1;
    }

    /**
     * 子树内是否包含公开 LEAF。
     */
    public static boolean hasPublicLeafInSubtree(
            Long nodeId,
            Map<Long, List<BankNode>> childrenByParent,
            Map<Long, BankNode> byId) {
        Set<Long> subtree = collectSubtreeIds(nodeId, childrenByParent);
        for (Long id : subtree) {
            BankNode node = byId.get(id);
            if (node != null && isPublicLeaf(node)) {
                return true;
            }
        }
        return false;
    }

    public static int countDescendantLeaves(
            Long nodeId,
            Map<Long, List<BankNode>> childrenByParent,
            Map<Long, BankNode> byId) {
        int count = 0;
        for (Long id : collectSubtreeIds(nodeId, childrenByParent)) {
            BankNode node = byId.get(id);
            if (node != null && BankNodeKind.LEAF.name().equals(node.getNodeKind())) {
                count++;
            }
        }
        return count;
    }

    public static Set<Long> computePublicVisibleIds(List<BankNode> allNodes) {
        Map<Long, BankNode> byId = new HashMap<>();
        for (BankNode node : allNodes) {
            byId.put(node.getId(), node);
        }
        Map<Long, List<BankNode>> childrenByParent = groupByParent(allNodes);
        Set<Long> visible = new HashSet<>();
        for (BankNode node : allNodes) {
            if (isPublicLeaf(node)) {
                visible.add(node.getId());
                Long parentId = node.getParentId();
                while (parentId != null) {
                    if (!visible.add(parentId)) {
                        break;
                    }
                    BankNode parent = byId.get(parentId);
                    parentId = parent == null ? null : parent.getParentId();
                }
            }
        }
        return visible;
    }

    public static Map<Long, BankNode> indexById(List<BankNode> nodes) {
        Map<Long, BankNode> byId = new HashMap<>();
        for (BankNode node : nodes) {
            byId.put(node.getId(), node);
        }
        return byId;
    }

    public static List<BankNode> filterSubtree(List<BankNode> nodes, Long rootId) {
        Map<Long, List<BankNode>> childrenByParent = groupByParent(nodes);
        Set<Long> subtreeIds = collectSubtreeIds(rootId, childrenByParent);
        return nodes.stream().filter(n -> subtreeIds.contains(n.getId())).toList();
    }

    public static List<BankNode> emptyIfNull(List<BankNode> nodes) {
        return nodes == null ? Collections.emptyList() : nodes;
    }
}
