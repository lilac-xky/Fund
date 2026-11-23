package com.lilac.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lilac.domain.vo.EstimateVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.EstimateTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预估视图序列化测试，确保内部区间中心值不会混进对外 JSON
 */
class EstimateVOSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 内部中心值只供留存与统计，序列化时必须被摘掉，否则等于对外给了单一数字
     *
     * @throws Exception 序列化失败
     */
    @Test
    @DisplayName("内部中心值不进入对外 JSON")
    void centerChangeIsNotSerialized() throws Exception {
        EstimateVO vo = new EstimateVO();
        vo.setVarietyKey("OF:011370");
        vo.setCategory(FundCategory.MIXED);
        vo.setTier(EstimateTier.B);
        vo.setCenterChange(new BigDecimal("2.03"));
        vo.setIntervalLow(new BigDecimal("-1.82"));
        vo.setIntervalHigh(new BigDecimal("5.88"));

        String json = objectMapper.writeValueAsString(vo);

        assertThat(json).doesNotContain("centerChange");
        assertThat(json).doesNotContain("2.03");
        // 区间上下界本身是可以对外说的，必须仍然存在
        assertThat(json).contains("intervalLow");
        assertThat(json).contains("intervalHigh");
    }
}
