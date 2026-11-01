package com.lilac.domain;

import com.lilac.domain.vo.DuplicateCleanVO;
import com.lilac.service.DuplicateCleanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 重复批次清理测试，重点是预览与执行前后的行数变化
 */
@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "fund.realDbTest", matches = "true")
class DuplicateCleanTest {

    @Autowired
    private DuplicateCleanService duplicateCleanService;

    /**
     * 预览要能列出重复组合，且每组至少两条
     */
    @Test
    @DisplayName("预览列出重复批次组合")
    void previewListsDuplicates() {
        List<DuplicateCleanVO> rows = duplicateCleanService.preview();
        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(row -> row.getCount() != null && row.getCount() > 1);
        assertThat(rows).allMatch(row -> row.getKeepId() != null);
        assertThat(rows).allMatch(row -> row.getRemoveIds() != null && !row.getRemoveIds().isBlank());
        rows.stream().limit(5).forEach(row -> System.out.println("重复 " + row.getVarietyName() + " "
                + row.getShares() + " 份 @ " + row.getCostPrice() + " 建仓 " + row.getOpenDate()
                + " 共 " + row.getCount() + " 条，保留 " + row.getKeepId() + " 删 " + row.getRemoveIds()));
    }
}
