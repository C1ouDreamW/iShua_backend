package cn.heycloudream.ishua_backend.enums;

import cn.heycloudream.ishua_backend.exception.BusinessException;

/**
 * 题库树查询范围。
 *
 * @author C1ouD
 */
public enum BankTreeScope {

    MINE,
    PUBLIC;

    public static BankTreeScope fromQueryValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "scope 不能为空");
        }
        try {
            return BankTreeScope.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "不支持的 scope：" + value);
        }
    }
}
