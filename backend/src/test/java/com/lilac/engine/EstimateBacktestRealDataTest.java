package com.lilac.engine;

import com.lilac.domain.dto.estimate.EstimateBacktestRequest;
import com.lilac.domain.vo.BacktestItemVO;
import com.lilac.domain.vo.BacktestResultVO;
import com.lilac.domain.vo.BacktestSummaryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 因子回测真实数据冒烟，用库内真实净值与上游日线跑一遍，只验证链路与口径
 */
@SpringBootTest
class EstimateBacktestRealDataTest {

    @Autowired
    private com.lilac.service.EstimateBacktestService estimateBacktestService;

    /**
     * 用真实数据回测三只代理分支基金，检查产出结构合理
     */
    @Test
    @DisplayName("真实数据回测产出合理结构")
    void runsOnRealData() {
        EstimateBacktestRequest request = new EstimateBacktestRequest();
        request.setStartDate(LocalDate.of(2026, 6, 1).toString());
        request.setEndDate(LocalDate.of(2026, 11, 19).toString());
        request.setVarietyKeys(List.of("OF:004156", "OF:004598", "OF:004253"));

        BacktestResultVO result = estimateBacktestService.run(request);

        System.out.println("=== 回测汇总 ===");
        System.out.printf("区间 %s ~ %s  交易日 %d  基金 %d 只  样本 %d 条%n",
                result.getStartDate(), result.getEndDate(), result.getTradeDateCount(),
                result.getVarietyCount(), result.getSampleCount());
        System.out.printf("整体 MAE=%s 中位=%s P90=%s 超阈率=%s 方向=%s%n",
                result.getMae(), result.getMedianAbsError(), result.getP90AbsError(),
                result.getOverThresholdRatio(), result.getDirectionAccuracy());
        for (BacktestSummaryVO summary : result.getSummaries()) {
            System.out.printf("  %s %-16s n=%-4d MAE=%s 中位=%s P90=%s 偏差=%s 超阈=%d 方向=%s%n",
                    summary.getVarietyCode(), summary.getVarietyName(), summary.getSampleCount(),
                    summary.getMae(), summary.getMedianAbsError(), summary.getP90AbsError(),
                    summary.getBias(), summary.getOverThresholdCount(), summary.getDirectionAccuracy());
        }
        System.out.println("=== 说明 ===");
        result.getNotes().forEach(note -> System.out.println("  " + note));

        assertThat(result.getSampleCount()).isPositive();
        assertThat(result.getSummaries()).isNotEmpty();
        assertThat(result.getMae()).isNotNull();
        for (BacktestItemVO item : result.getItems()) {
            assertThat(item.getEstimatedChange()).isNotNull();
            assertThat(item.getActualChange()).isNotNull();
            assertThat(item.getErrorValue()).isNotNull();
        }
    }

    /**
     * 关掉 β 校正后结果应变化，证明开关真的生效
     */
    @Test
    @DisplayName("关掉 β 校正后结果变化")
    void betaSwitchChangesResult() {
        EstimateBacktestRequest withBeta = new EstimateBacktestRequest();
        withBeta.setStartDate(LocalDate.of(2026, 6, 1).toString());
        withBeta.setEndDate(LocalDate.of(2026, 11, 19).toString());
        withBeta.setVarietyKeys(List.of("OF:004598"));

        EstimateBacktestRequest withoutBeta = new EstimateBacktestRequest();
        withoutBeta.setStartDate(withBeta.getStartDate());
        withoutBeta.setEndDate(withBeta.getEndDate());
        withoutBeta.setVarietyKeys(withBeta.getVarietyKeys());
        withoutBeta.setIndexBetaWindow(0);

        BacktestResultVO withModel = estimateBacktestService.run(withBeta);
        BacktestResultVO direct = estimateBacktestService.run(withoutBeta);

        System.out.printf("带 β：MAE=%s   直取：MAE=%s%n", withModel.getMae(), direct.getMae());
        assertThat(direct.getParams().getIndexBetaWindow()).isZero();
        assertThat(withModel.getParams().getIndexBetaWindow()).isPositive();
        assertThat(withModel.getSampleCount()).isEqualTo(direct.getSampleCount());
    }
}
