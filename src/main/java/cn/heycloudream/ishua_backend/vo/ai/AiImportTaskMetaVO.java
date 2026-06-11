package cn.heycloudream.ishua_backend.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务元数据，写入 Redis 供消费者和 Watchdog 使用。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiImportTaskMetaVO {

    /** 任务 ID（UUID）。 */
    private String taskId;

    /** 提交任务的用户 ID。 */
    private Long userId;

    /** 目标题库 ID。 */
    private Long bankId;

    /** 原始文件名。 */
    private String fileName;

    /** 文件大小（字节）。 */
    private Long fileSize;

    /** 文件存储 URL（如 file:// 或 MinIO URL）。 */
    private String fileUrl;

    /** 提交时间戳（epoch 毫秒）。 */
    private Long submittedAt;

    /** 导入类型：text / file。 */
    private String type;

    /** 纯文本内容（仅 type=text 时有值）。 */
    private String plainText;
}
