package cn.heycloudream.ishua_backend.mapper;

import cn.heycloudream.ishua_backend.entity.AiImportTask;
import cn.heycloudream.ishua_backend.mapper.row.AiImportTaskOverallStatsRow;
import cn.heycloudream.ishua_backend.mapper.row.AiImportTaskStatusAggRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 导入任务 Mapper。
 *
 * @author C1ouD
 */
@Mapper
public interface AiImportTaskMapper extends BaseMapper<AiImportTask> {

    /**
     * 统计窗口内各状态任务数及平均解析耗时。
     */
    @Select("""
            SELECT status,
                   COUNT(*) AS cnt,
                   AVG(pipeline_duration_ms) / 1000 AS avg_parse_sec
            FROM ai_import_task
            WHERE is_deleted = 0
              AND submitted_at >= #{periodStart}
            GROUP BY status
            """)
    List<AiImportTaskStatusAggRow> selectStatusAggSince(@Param("periodStart") LocalDateTime periodStart);

    /**
     * 统计窗口内任务总量、平均解析耗时、平均题目数与失败数。
     */
    @Select("""
            SELECT COUNT(*) AS total_count,
                   AVG(pipeline_duration_ms) / 1000 AS avg_pipeline_sec,
                   AVG(mineru_duration_ms) / 1000 AS avg_mineru_sec,
                   AVG(llm_duration_ms) / 1000 AS avg_llm_sec,
                   AVG(question_count) AS avg_question_count,
                   SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count
            FROM ai_import_task
            WHERE is_deleted = 0
              AND submitted_at >= #{periodStart}
            """)
    AiImportTaskOverallStatsRow selectOverallStatsSince(@Param("periodStart") LocalDateTime periodStart);
}
