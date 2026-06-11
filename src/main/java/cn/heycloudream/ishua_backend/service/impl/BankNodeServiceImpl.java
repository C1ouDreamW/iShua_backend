package cn.heycloudream.ishua_backend.service.impl;

import cn.heycloudream.ishua_backend.common.dto.PageRequestDTO;
import cn.heycloudream.ishua_backend.common.vo.PageResultVO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeCreateDTO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeMoveDTO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeSubtreeQueryDTO;
import cn.heycloudream.ishua_backend.dto.banknode.BankNodeUpdateDTO;
import cn.heycloudream.ishua_backend.dto.questionbank.QuestionBankCreateDTO;
import cn.heycloudream.ishua_backend.dto.questionbank.QuestionBankUpdateDTO;
import cn.heycloudream.ishua_backend.entity.BankNode;
import cn.heycloudream.ishua_backend.enums.BankNodeKind;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.mapper.BankNodeMapper;
import cn.heycloudream.ishua_backend.service.BankNodeService;
import cn.heycloudream.ishua_backend.service.QuestionService;
import cn.heycloudream.ishua_backend.service.cache.QuestionBankDetailCacheEvictor;
import cn.heycloudream.ishua_backend.service.guard.BankAccessGuard;
import cn.heycloudream.ishua_backend.service.support.BankNodeTreeSupport;
import cn.heycloudream.ishua_backend.vo.banknode.BankNodeVO;
import cn.heycloudream.ishua_backend.vo.questionbank.QuestionBankVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题库树节点服务实现。
 *
 * @author C1ouD
 */
@Service
public class BankNodeServiceImpl implements BankNodeService {

    private final BankNodeMapper bankNodeMapper;
    private final QuestionService questionService;
    private final QuestionBankDetailCacheEvictor questionBankDetailCacheEvictor;
    private final BankAccessGuard bankAccessGuard;

    public BankNodeServiceImpl(
            BankNodeMapper bankNodeMapper,
            @Lazy QuestionService questionService,
            QuestionBankDetailCacheEvictor questionBankDetailCacheEvictor,
            BankAccessGuard bankAccessGuard) {
        this.bankNodeMapper = bankNodeMapper;
        this.questionService = questionService;
        this.questionBankDetailCacheEvictor = questionBankDetailCacheEvictor;
        this.bankAccessGuard = bankAccessGuard;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNode(Long currentUserId, BankNodeCreateDTO dto) {
        BankNodeKind kind = BankNodeKind.fromDbValue(dto.getNodeKind());
        validateParentForCreate(currentUserId, dto.getParentId(), kind);
        LocalDateTime now = LocalDateTime.now();
        BankNode entity = BankNode.builder()
                .userId(currentUserId)
                .parentId(dto.getParentId())
                .nodeKind(kind.name())
                .title(dto.getTitle().trim())
                .description(trimToNull(dto.getDescription()))
                .isPublic(resolveIsPublic(kind, dto.getIsPublic()))
                .sortNo(dto.getSortNo() == null ? nextSortNo(currentUserId, dto.getParentId()) : dto.getSortNo())
                .questionCount(0)
                .createTime(now)
                .updateTime(now)
                .isDeleted(0)
                .build();
        bankNodeMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNode(Long currentUserId, Long nodeId, BankNodeUpdateDTO dto) {
        BankNode node = bankAccessGuard.requireOwnedNode(currentUserId, nodeId);
        BankNodeKind kind = BankNodeKind.fromDbValue(node.getNodeKind());
        node.setTitle(dto.getTitle().trim());
        node.setDescription(trimToNull(dto.getDescription()));
        if (dto.getIsPublic() != null) {
            node.setIsPublic(resolveIsPublic(kind, dto.getIsPublic()));
        }
        if (dto.getSortNo() != null) {
            node.setSortNo(dto.getSortNo());
        }
        node.setUpdateTime(LocalDateTime.now());
        bankNodeMapper.updateById(node);
        evictIfPublicLeaf(node);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long currentUserId, Long nodeId) {
        bankAccessGuard.requireOwnedNode(currentUserId, nodeId);
        deleteSubtreeOwnedByUser(currentUserId, nodeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveNode(Long currentUserId, Long nodeId, BankNodeMoveDTO dto) {
        BankNode node = bankAccessGuard.requireOwnedNode(currentUserId, nodeId);
        Long newParentId = dto.getNewParentId();
        List<BankNode> owned = listAllByUser(currentUserId);
        if (newParentId != null) {
            BankNode parent = bankAccessGuard.requireOwnedNode(currentUserId, newParentId);
            bankAccessGuard.requireFolder(parent);
            if (nodeId.equals(newParentId)) {
                throw new BusinessException(400, "不能将节点移动到自身");
            }
            Map<Long, BankNode> byId = BankNodeTreeSupport.indexById(owned);
            if (BankNodeTreeSupport.isDescendant(nodeId, newParentId, byId)) {
                throw new BusinessException(400, "不能将节点移动到其子节点下");
            }
        }

        Long oldParentId = node.getParentId();
        Map<Long, List<BankNode>> childrenByParent = BankNodeTreeSupport.groupByParent(owned);
        List<BankNode> targetSiblings = new ArrayList<>(
                childrenByParent.getOrDefault(newParentId, List.of()));
        targetSiblings.removeIf(current -> nodeId.equals(current.getId()));

        int targetIndex = dto.getNewSortNo() != null ? dto.getNewSortNo() : targetSiblings.size();
        targetIndex = Math.min(Math.max(targetIndex, 0), targetSiblings.size());

        node.setParentId(newParentId);
        targetSiblings.add(targetIndex, node);

        LocalDateTime now = LocalDateTime.now();
        renumberSiblingSortNos(targetSiblings, now);

        if (!Objects.equals(oldParentId, newParentId)) {
            List<BankNode> oldSiblings = new ArrayList<>(
                    childrenByParent.getOrDefault(oldParentId, List.of()));
            oldSiblings.removeIf(current -> nodeId.equals(current.getId()));
            renumberSiblingSortNos(oldSiblings, now);
        }
    }

    @Override
    public BankNodeVO getNode(Long currentUserId, Long nodeId) {
        BankNode node = bankAccessGuard.requireOwnedNode(currentUserId, nodeId);
        return toVoWithStats(node, listAllByUser(currentUserId));
    }

    @Override
    public PageResultVO<BankNodeVO> pagePublicRoots(PageRequestDTO query) {
        List<BankNode> roots = new ArrayList<>(filterPublicRoots(listAllNodes()));
        roots.sort(rootComparator());
        return paginateRoots(roots, query.getCurrent(), query.getPageSize(), listAllNodes());
    }

    @Override
    public List<BankNodeVO> listPublicTree(BankNodeSubtreeQueryDTO query) {
        List<BankNode> source = filterPublicVisibleNodes(listAllNodes());
        if (query.getRootId() != null) {
            source = BankNodeTreeSupport.filterSubtree(source, query.getRootId());
        }
        return toVoListWithStats(source);
    }

    @Override
    public PageResultVO<BankNodeVO> pageMyRoots(Long currentUserId, PageRequestDTO query) {
        List<BankNode> roots = new ArrayList<>(filterMineRoots(listAllByUser(currentUserId)));
        roots.sort(rootComparator());
        return paginateRoots(roots, query.getCurrent(), query.getPageSize(), listAllByUser(currentUserId));
    }

    @Override
    public List<BankNodeVO> listMyTree(Long currentUserId, BankNodeSubtreeQueryDTO query) {
        List<BankNode> source = listAllByUser(currentUserId);
        if (query.getRootId() != null) {
            bankAccessGuard.requireOwnedNode(currentUserId, query.getRootId());
            source = BankNodeTreeSupport.filterSubtree(source, query.getRootId());
        }
        return toVoListWithStats(source);
    }

    @Override
    public PageResultVO<QuestionBankVO> pageMyLeaves(Long currentUserId, PageRequestDTO page) {
        Page<BankNode> mp = new Page<>(page.getCurrent(), page.getPageSize());
        LambdaQueryWrapper<BankNode> wrapper = new LambdaQueryWrapper<BankNode>()
                .eq(BankNode::getUserId, currentUserId)
                .eq(BankNode::getNodeKind, BankNodeKind.LEAF.name())
                .orderByDesc(BankNode::getUpdateTime);
        bankNodeMapper.selectPage(mp, wrapper);
        List<QuestionBankVO> records = mp.getRecords().stream().map(this::toQuestionBankVo).collect(Collectors.toList());
        return PageResultVO.<QuestionBankVO>builder().total(mp.getTotal()).records(records).build();
    }

    @Override
    public PageResultVO<QuestionBankVO> pagePublicLeaves(PageRequestDTO page) {
        Page<BankNode> mp = new Page<>(page.getCurrent(), page.getPageSize());
        LambdaQueryWrapper<BankNode> wrapper = new LambdaQueryWrapper<BankNode>()
                .eq(BankNode::getNodeKind, BankNodeKind.LEAF.name())
                .eq(BankNode::getIsPublic, 1)
                .orderByDesc(BankNode::getUpdateTime);
        bankNodeMapper.selectPage(mp, wrapper);
        List<QuestionBankVO> records = mp.getRecords().stream().map(this::toQuestionBankVo).collect(Collectors.toList());
        return PageResultVO.<QuestionBankVO>builder().total(mp.getTotal()).records(records).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLeafAtRoot(Long currentUserId, QuestionBankCreateDTO dto) {
        BankNodeCreateDTO createDto = BankNodeCreateDTO.builder()
                .parentId(null)
                .nodeKind(BankNodeKind.LEAF.name())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .isPublic(dto.getIsPublic())
                .build();
        return createNode(currentUserId, createDto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLeaf(Long currentUserId, Long leafId, QuestionBankUpdateDTO dto) {
        BankNode node = bankAccessGuard.requireOwnedLeaf(currentUserId, leafId);
        node.setTitle(dto.getTitle().trim());
        node.setDescription(trimToNull(dto.getDescription()));
        node.setIsPublic(dto.getIsPublic());
        node.setUpdateTime(LocalDateTime.now());
        bankNodeMapper.updateById(node);
        evictIfPublicLeaf(node);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLeaf(Long currentUserId, Long leafId) {
        bankAccessGuard.requireOwnedLeaf(currentUserId, leafId);
        deleteSubtreeOwnedByUser(currentUserId, leafId);
    }

    @Override
    public void adjustQuestionCount(Long leafId, int delta) {
        if (leafId == null || delta == 0) {
            return;
        }
        BankNode node = bankNodeMapper.selectById(leafId);
        if (node == null || !BankNodeKind.LEAF.name().equals(node.getNodeKind())) {
            return;
        }
        int current = node.getQuestionCount() == null ? 0 : node.getQuestionCount();
        node.setQuestionCount(Math.max(0, current + delta));
        node.setUpdateTime(LocalDateTime.now());
        bankNodeMapper.updateById(node);
    }

    @Override
    public void resetQuestionCount(Long leafId, int count) {
        if (leafId == null) {
            return;
        }
        BankNode node = bankNodeMapper.selectById(leafId);
        if (node == null || !BankNodeKind.LEAF.name().equals(node.getNodeKind())) {
            return;
        }
        node.setQuestionCount(Math.max(0, count));
        node.setUpdateTime(LocalDateTime.now());
        bankNodeMapper.updateById(node);
    }

    @Override
    public BankNode requireExistsNode(Long nodeId) {
        BankNode node = bankNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new BusinessException(404, "节点不存在");
        }
        return node;
    }

    private void deleteSubtreeOwnedByUser(Long currentUserId, Long nodeId) {
        List<BankNode> owned = listAllByUser(currentUserId);
        Map<Long, List<BankNode>> childrenByParent = BankNodeTreeSupport.groupByParent(owned);
        Set<Long> subtreeIds = BankNodeTreeSupport.collectSubtreeIds(nodeId, childrenByParent);
        Map<Long, BankNode> byId = BankNodeTreeSupport.indexById(owned);
        for (Long id : subtreeIds) {
            BankNode current = byId.get(id);
            if (current != null && BankNodeKind.LEAF.name().equals(current.getNodeKind())) {
                questionService.removeQuestionsByBankId(id);
                questionBankDetailCacheEvictor.evict(id);
            }
        }
        for (Long id : subtreeIds) {
            bankNodeMapper.deleteById(id);
        }
    }

    private void validateParentForCreate(Long currentUserId, Long parentId, BankNodeKind kind) {
        if (parentId == null) {
            return;
        }
        BankNode parent = bankAccessGuard.requireOwnedNode(currentUserId, parentId);
        bankAccessGuard.requireFolder(parent);
        if (BankNodeKind.LEAF == kind && BankNodeKind.LEAF.name().equals(parent.getNodeKind())) {
            throw new BusinessException(400, "题库节点不能再拥有子节点");
        }
    }

    private List<BankNode> listAllByUser(Long userId) {
        return bankNodeMapper.selectList(new LambdaQueryWrapper<BankNode>()
                .eq(BankNode::getUserId, userId)
                .orderByAsc(BankNode::getSortNo)
                .orderByAsc(BankNode::getId));
    }

    private List<BankNode> listAllNodes() {
        return bankNodeMapper.selectList(new LambdaQueryWrapper<BankNode>()
                .orderByAsc(BankNode::getSortNo)
                .orderByAsc(BankNode::getId));
    }

    private List<BankNode> filterMineRoots(List<BankNode> nodes) {
        return nodes.stream().filter(n -> n.getParentId() == null).toList();
    }

    private List<BankNode> filterPublicRoots(List<BankNode> allNodes) {
        Map<Long, BankNode> byId = BankNodeTreeSupport.indexById(allNodes);
        Map<Long, List<BankNode>> childrenByParent = BankNodeTreeSupport.groupByParent(allNodes);
        return allNodes.stream()
                .filter(n -> n.getParentId() == null)
                .filter(n -> BankNodeTreeSupport.isPublicLeaf(n)
                        || BankNodeTreeSupport.hasPublicLeafInSubtree(n.getId(), childrenByParent, byId))
                .toList();
    }

    private List<BankNode> filterPublicVisibleNodes(List<BankNode> allNodes) {
        Set<Long> visibleIds = BankNodeTreeSupport.computePublicVisibleIds(allNodes);
        return allNodes.stream().filter(n -> visibleIds.contains(n.getId())).toList();
    }

    private PageResultVO<BankNodeVO> paginateRoots(
            List<BankNode> roots, long current, long pageSize, List<BankNode> statsSource) {
        int from = (int) Math.max(0, (current - 1) * pageSize);
        int to = (int) Math.min(roots.size(), from + pageSize);
        List<BankNodeVO> records = from >= roots.size()
                ? List.of()
                : roots.subList(from, to).stream()
                .map(node -> toVoWithStats(node, statsSource))
                .toList();
        return PageResultVO.<BankNodeVO>builder()
                .total((long) roots.size())
                .records(records)
                .build();
    }

    private List<BankNodeVO> toVoListWithStats(List<BankNode> nodes) {
        Map<Long, List<BankNode>> childrenByParent = BankNodeTreeSupport.groupByParent(nodes);
        Map<Long, BankNode> byId = BankNodeTreeSupport.indexById(nodes);
        List<BankNodeVO> result = new ArrayList<>(nodes.size());
        for (BankNode node : nodes) {
            result.add(toVoWithStats(node, childrenByParent, byId));
        }
        return result;
    }

    private BankNodeVO toVoWithStats(BankNode node, List<BankNode> statsSource) {
        Map<Long, List<BankNode>> childrenByParent = BankNodeTreeSupport.groupByParent(statsSource);
        Map<Long, BankNode> byId = BankNodeTreeSupport.indexById(statsSource);
        return toVoWithStats(node, childrenByParent, byId);
    }

    private BankNodeVO toVoWithStats(
            BankNode node, Map<Long, List<BankNode>> childrenByParent, Map<Long, BankNode> byId) {
        int childCount = childrenByParent.getOrDefault(node.getId(), List.of()).size();
        int descendantLeafCount = BankNodeTreeSupport.countDescendantLeaves(node.getId(), childrenByParent, byId);
        if (BankNodeKind.LEAF.name().equals(node.getNodeKind())) {
            descendantLeafCount = Math.max(descendantLeafCount, 1);
        }
        boolean hasPublicDescendant = BankNodeTreeSupport.hasPublicLeafInSubtree(node.getId(), childrenByParent, byId);
        return BankNodeVO.builder()
                .id(node.getId())
                .userId(node.getUserId())
                .parentId(node.getParentId())
                .nodeKind(node.getNodeKind())
                .title(node.getTitle())
                .description(node.getDescription())
                .isPublic(node.getIsPublic())
                .sortNo(node.getSortNo())
                .questionCount(node.getQuestionCount())
                .childCount(childCount)
                .descendantLeafCount(descendantLeafCount)
                .hasPublicDescendant(hasPublicDescendant)
                .createTime(node.getCreateTime())
                .updateTime(node.getUpdateTime())
                .build();
    }

    private QuestionBankVO toQuestionBankVo(BankNode node) {
        return QuestionBankVO.builder()
                .id(node.getId())
                .userId(node.getUserId())
                .title(node.getTitle())
                .description(node.getDescription())
                .isPublic(node.getIsPublic())
                .createTime(node.getCreateTime())
                .updateTime(node.getUpdateTime())
                .build();
    }

    private void renumberSiblingSortNos(List<BankNode> siblings, LocalDateTime now) {
        for (int index = 0; index < siblings.size(); index++) {
            BankNode sibling = siblings.get(index);
            if (!Integer.valueOf(index).equals(sibling.getSortNo())) {
                sibling.setSortNo(index);
                sibling.setUpdateTime(now);
                bankNodeMapper.updateById(sibling);
            }
        }
    }

    private int nextSortNo(Long userId, Long parentId) {
        BankNode max = bankNodeMapper.selectOne(new LambdaQueryWrapper<BankNode>()
                .eq(BankNode::getUserId, userId)
                .isNull(parentId == null, BankNode::getParentId)
                .eq(parentId != null, BankNode::getParentId, parentId)
                .orderByDesc(BankNode::getSortNo)
                .last("LIMIT 1"));
        if (max == null || max.getSortNo() == null) {
            return 0;
        }
        return max.getSortNo() + 1;
    }

    private Integer resolveIsPublic(BankNodeKind kind, Integer isPublic) {
        if (kind == BankNodeKind.FOLDER) {
            return 0;
        }
        return isPublic == null ? 0 : isPublic;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void evictIfPublicLeaf(BankNode node) {
        if (node != null
                && BankNodeKind.LEAF.name().equals(node.getNodeKind())
                && node.getIsPublic() != null
                && node.getIsPublic() == 1) {
            questionBankDetailCacheEvictor.evict(node.getId());
        }
    }

    private static Comparator<BankNode> rootComparator() {
        return Comparator.comparing((BankNode n) -> n.getSortNo() == null ? 0 : n.getSortNo())
                .thenComparing(BankNode::getId, Comparator.nullsLast(Long::compareTo));
    }
}
