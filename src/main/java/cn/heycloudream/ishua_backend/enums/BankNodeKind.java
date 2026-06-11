package cn.heycloudream.ishua_backend.enums;

import cn.heycloudream.ishua_backend.exception.BusinessException;

/**
 * 题库树节点类型：文件夹容器或可挂题的叶子题库。
 *
 * @author C1ouD
 */
public enum BankNodeKind {

    FOLDER,
    LEAF;

    public static BankNodeKind fromDbValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "节点类型不能为空");
        }
        try {
            return BankNodeKind.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "不支持的节点类型：" + value);
        }
    }
}
