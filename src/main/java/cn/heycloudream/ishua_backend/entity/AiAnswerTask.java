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
 * AI 解答任务持久化实体，对应数据库表 {@code ai_answer_task}（阶段 2）。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_answer_task")
public class AiAnswerTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String answerTaskId;

    private String parentTaskId;

    private Long userId;

    private Long bankId;

    private Integer questionCount;

    private Integer answeredCount;

    private String status;

    private String answeredJson;

    private String errorMessage;

    private Integer llmDurationMs;

    private Integer totalCalls;

    private LocalDateTime submittedAt;

    private LocalDateTime answeredAt;

    private LocalDateTime importedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
