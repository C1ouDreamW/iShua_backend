package cn.heycloudream.ishua_backend.support;

import cn.heycloudream.ishua_backend.entity.BankNode;
import cn.heycloudream.ishua_backend.mapper.BankNodeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 轨道 A 验收：H2 schema.sql + data.sql 下 MyBatis-Plus 可正常访问种子数据。
 */
class H2SchemaSmokeTest extends AbstractMockRedisSpringBootTest {

    @Autowired
    private BankNodeMapper bankNodeMapper;

    @Test
    @DisplayName("H2 种子数据：公开 LEAF 节点 id=1 可查询")
    void shouldLoadSeedBankNode() {
        BankNode node = bankNodeMapper.selectById(1L);
        assertThat(node).isNotNull();
        assertThat(node.getUserId()).isEqualTo(1L);
        assertThat(node.getNodeKind()).isEqualTo("LEAF");
        assertThat(node.getIsPublic()).isEqualTo(1);
        assertThat(node.getQuestionCount()).isEqualTo(2);
    }
}
