package cn.heycloudream.ishua_backend.mapper.row;

import lombok.Data;

/**
 * {@code ai_import_task} 按 status 聚合查询行。
 *
 * @author C1ouD
 */
@Data
public class AiImportTaskStatusAggRow {

    private String status;

    private Long cnt;

    private Double avgParseSec;
}
