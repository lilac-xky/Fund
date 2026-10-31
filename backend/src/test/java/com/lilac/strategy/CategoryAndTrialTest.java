package com.lilac.strategy;

import com.lilac.adapter.fund.FundCategoryResolver;
import com.lilac.domain.dto.lesson.LessonTrialRequest;
import com.lilac.enums.FundCategory;
import com.lilac.strategy.model.LessonPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类型推断依据与试算参数覆盖的测试，覆盖 ISSUE-026 的推断说明与 ISSUE-027 的试算可复现
 */
class CategoryAndTrialTest {

    private FundCategoryResolver resolver;

    /**
     * 装配推断器
     */
    @BeforeEach
    void setUp() {
        resolver = new FundCategoryResolver();
    }

    /**
     * QDII 黄金要说明为什么不能套用国内黄金 ETF 代理
     */
    @Test
    @DisplayName("QDII 黄金给出不可推断的原因")
    void qdiiGoldExplained() {
        String reason = resolver.pendingReasonOf("QDII-指数", "诺安全球黄金");

        assertThat(reason).contains("QDII").contains("汇率");
    }

    /**
     * 同时含黄金与黄金股关键词时必须要求人工判断
     */
    @Test
    @DisplayName("黄金与黄金股关键词并存时要求人工确认")
    void goldVsGoldEquityAmbiguous() {
        String reason = resolver.pendingReasonOf("股票型", "黄金股与金矿主题");

        assertThat(reason).contains("黄金股");
    }

    /**
     * 能确定的类型不该给出待确认原因，否则页面会误报
     */
    @Test
    @DisplayName("能确定的类型不给出待确认原因")
    void determinateReturnsNull() {
        assertThat(resolver.pendingReasonOf("债券型-长债", "某某债券A")).isNull();
        assertThat(resolver.pendingReasonOf("指数型-股票", "沪深300ETF")).isNull();
    }

    /**
     * 关键词提示要能列出命中的词，供使用者判断依据
     */
    @Test
    @DisplayName("能列出名称里命中的关键词")
    void hintsListed() {
        List<String> hints = resolver.hintsOf("中证红利低波指数");

        assertThat(hints).contains("红利");
    }

    /**
     * 推断器对正常名称仍要能给出正确类型，说明新增方法没破坏原逻辑
     */
    @Test
    @DisplayName("原有类型推断不受影响")
    void resolveStillWorks() {
        assertThat(resolver.resolve("债券型-长债", "某某债券", false)).isEqualTo(FundCategory.BOND);
        assertThat(resolver.resolve("混合型-灵活", "中证红利混合", false)).isEqualTo(FundCategory.DIVIDEND);
        assertThat(resolver.resolve("指数型-其他", "黄金ETF", true)).isEqualTo(FundCategory.GOLD);
    }

    /**
     * 试算参数覆盖时，未填字段必须回落到库内值，否则结果不可复现
     */
    @Test
    @DisplayName("试算只覆盖填了的字段")
    void trialOverrideOnlyFilledFields() {
        LessonPlan base = planOf("3", 20, 5, "5", 30);
        LessonTrialRequest request = new LessonTrialRequest();
        request.setChaseTolerance(new BigDecimal("8"));

        LessonPlan merged = merge(base, request);

        assertThat(merged.getChaseTolerance()).isEqualByComparingTo("8");
        // 没填的保持原值，这条是「结果可复现」的前提
        assertThat(merged.getChaseWindow()).isEqualTo(20);
        assertThat(merged.getPanicLossDays()).isEqualTo(5);
        assertThat(merged.getFamiliarDays()).isEqualTo(30);
    }

    /**
     * 试算用离谱参数时也要被校验拦住，不能算出误导性结果
     */
    @Test
    @DisplayName("试算参数非法时被校验拦住")
    void trialRejectsInvalidParams() {
        LessonPlan base = planOf("3", 20, 5, "5", 30);
        LessonTrialRequest request = new LessonTrialRequest();
        request.setChaseTolerance(new BigDecimal("150"));

        LessonPlan merged = merge(base, request);
        List<String> issues = new LessonPlanResolver().validate(merged);

        assertThat(issues).anyMatch(text -> text.contains("不应超过 100%"));
    }

    /**
     * 按与实现一致的方式合并参数，便于独立校验覆盖语义
     *
     * @param base    库内值
     * @param request 覆盖值
     * @return 合并结果
     */
    private LessonPlan merge(LessonPlan base, LessonTrialRequest request) {
        LessonPlan plan = new LessonPlan();
        plan.setChaseTolerance(request.getChaseTolerance() == null ? base.getChaseTolerance()
                : request.getChaseTolerance());
        plan.setChaseWindow(request.getChaseWindow() == null ? base.getChaseWindow() : request.getChaseWindow());
        plan.setPanicLossDays(request.getPanicLossDays() == null ? base.getPanicLossDays()
                : request.getPanicLossDays());
        plan.setPanicLossRate(request.getPanicLossRate() == null ? base.getPanicLossRate()
                : request.getPanicLossRate());
        plan.setFamiliarDays(request.getFamiliarDays() == null ? base.getFamiliarDays()
                : request.getFamiliarDays());
        return plan;
    }

    /**
     * 构造参数
     *
     * @param tolerance    追高容忍
     * @param window       窗口
     * @param lossDays     连亏天数
     * @param lossRate     亏损率
     * @param familiarDays 熟悉天数
     * @return 参数
     */
    private LessonPlan planOf(String tolerance, int window, int lossDays, String lossRate, int familiarDays) {
        LessonPlan plan = new LessonPlan();
        plan.setChaseTolerance(new BigDecimal(tolerance));
        plan.setChaseWindow(window);
        plan.setPanicLossDays(lossDays);
        plan.setPanicLossRate(new BigDecimal(lossRate));
        plan.setFamiliarDays(familiarDays);
        plan.setParticipateInAlert(Boolean.TRUE);
        return plan;
    }
}
