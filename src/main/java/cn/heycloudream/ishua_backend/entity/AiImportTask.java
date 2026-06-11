package cn.heycloudream.ishua_backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 导入任务持久化实体，对应数据库表 {@code ai_import_task}。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_import_task")
public class AiImportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    private Long userId;

    private Long bankId;

    private String status;

    private String fileName;

    private Long fileSize;

    private String fileUrl;

    private String importType;

    private Integer questionCount;

    private String previewJson;

    private String errorMessage;

    private LocalDateTime submittedAt;

    private LocalDateTime parsedAt;

    private LocalDateTime importedAt;

    private LocalDateTime expiredAt;

    private Integer mineruDurationMs;

    private Integer llmDurationMs;

    private Integer pipelineDurationMs;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
