package cn.heycloudream.ishua_backend.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * AI 导入任务 ID 生成器（UUID v4，去连字符）。
 *
 * @author C1ouD
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TaskIdGenerator {

    /**
     * 生成短格式任务 ID（32 字符，不含连字符）。
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
