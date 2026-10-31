package com.lilac.strategy;

import com.lilac.constant.IndicatorParamKey;
import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.vo.StrategyParamVO;
import com.lilac.enums.ParamGroup;
import com.lilac.service.StrategyParamService;
import com.lilac.strategy.model.IndicatorPlan;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技术指标参数装配器，读取 strategy_param 的 INDICATOR 分组并补齐内置默认值
 */
@Slf4j
@Component
public class IndicatorPlanResolver {

    @Resource
    private StrategyParamService strategyParamService;

    /**
     * 读取指标参数，库里没有的字段回落到内置默认值
     *
     * @return 参数，含问题清单
     */
    public IndicatorPlan resolve() {
        Map<String, String> values = readValues();
        IndicatorPlan plan = new IndicatorPlan();
        plan.setRsiPeriod(integer(values, IndicatorParamKey.RSI_PERIOD, IndicatorParamKey.DEFAULT_RSI_PERIOD));
        plan.setRsiUpper(decimal(values, IndicatorParamKey.RSI_UPPER, IndicatorParamKey.DEFAULT_RSI_UPPER));
        plan.setRsiLower(decimal(values, IndicatorParamKey.RSI_LOWER, IndicatorParamKey.DEFAULT_RSI_LOWER));
        plan.setKdjPeriod(integer(values, IndicatorParamKey.KDJ_PERIOD, IndicatorParamKey.DEFAULT_KDJ_PERIOD));
        plan.setKdjKSmooth(integer(values, IndicatorParamKey.KDJ_K_SMOOTH, IndicatorParamKey.DEFAULT_KDJ_K_SMOOTH));
        plan.setKdjDSmooth(integer(values, IndicatorParamKey.KDJ_D_SMOOTH, IndicatorParamKey.DEFAULT_KDJ_D_SMOOTH));
        plan.setMacdFast(integer(values, IndicatorParamKey.MACD_FAST, IndicatorParamKey.DEFAULT_MACD_FAST));
        plan.setMacdSlow(integer(values, IndicatorParamKey.MACD_SLOW, IndicatorParamKey.DEFAULT_MACD_SLOW));
        plan.setMacdSignal(integer(values, IndicatorParamKey.MACD_SIGNAL, IndicatorParamKey.DEFAULT_MACD_SIGNAL));
        plan.setParticipateInAlert(flag(values, IndicatorParamKey.PARTICIPATE_IN_ALERT,
                IndicatorParamKey.DEFAULT_PARTICIPATE_IN_ALERT));
        plan.setIssues(validate(plan));
        return plan;
    }

    /**
     * 校验参数自洽性，周期至少 2 日，慢线须大于快线，阈值顺序须正确
     *
     * @param plan 参数
     * @return 问题清单，空表示可用
     */
    public List<String> validate(IndicatorPlan plan) {
        List<String> issues = new ArrayList<>();
        if (plan == null) {
            issues.add("指标参数不能为空");
            return issues;
        }
        checkPeriod(issues, plan.getRsiPeriod(), "RSI 周期");
        checkPeriod(issues, plan.getKdjPeriod(), "KDJ 的 RSV 周期");
        checkPeriod(issues, plan.getMacdFast(), "MACD 快线周期");
        checkPeriod(issues, plan.getMacdSlow(), "MACD 慢线周期");
        checkPeriod(issues, plan.getMacdSignal(), "MACD 信号线周期");
        if (plan.getKdjKSmooth() == null || plan.getKdjKSmooth() < 2) {
            issues.add("KDJ 的 K 平滑系数至少为 2");
        }
        if (plan.getKdjDSmooth() == null || plan.getKdjDSmooth() < 2) {
            issues.add("KDJ 的 D 平滑系数至少为 2");
        }
        if (plan.getMacdFast() != null && plan.getMacdSlow() != null
                && plan.getMacdFast() >= plan.getMacdSlow()) {
            issues.add("MACD 慢线周期必须大于快线周期 " + plan.getMacdFast());
        }
        if (plan.getRsiUpper() == null || plan.getRsiLower() == null) {
            issues.add("RSI 超买与超卖阈值必须同时设置");
        } else if (plan.getRsiLower().compareTo(plan.getRsiUpper()) >= 0) {
            issues.add("RSI 超卖阈值必须低于超买阈值 " + plain(plan.getRsiUpper()));
        }
        return issues;
    }

    /**
     * 校验周期下限，1 日周期算不出有意义的值
     *
     * @param issues 问题清单
     * @param period 周期
     * @param label  字段中文名
     */
    private void checkPeriod(List<String> issues, Integer period, String label) {
        if (period == null || period < 2) {
            issues.add(label + "至少为 2 日，否则指标无意义");
        }
    }

    /**
     * 读库内全部 INDICATOR 参数
     *
     * @return 参数名到参数值的映射
     */
    private Map<String, String> readValues() {
        ParamQueryRequest query = new ParamQueryRequest();
        query.setParamGroup(ParamGroup.INDICATOR);
        Map<String, String> values = new LinkedHashMap<>();
        for (StrategyParamVO vo : strategyParamService.listParam(query)) {
            if (vo.getParamKey() != null) {
                values.put(vo.getParamKey(), vo.getParamValue());
            }
        }
        return values;
    }

    /**
     * 取整数字段，库值优先、默认值兜底
     *
     * @param values       库内取值
     * @param key          参数名
     * @param defaultValue 默认值
     * @return 整数，非法返回 null
     */
    private Integer integer(Map<String, String> values, String key, String defaultValue) {
        String raw = values.get(key);
        String value = raw == null || raw.isBlank() ? defaultValue : raw;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("指标参数不是合法整数 key={} value={}", key, value);
            return null;
        }
    }

    /**
     * 取小数字段，库值优先、默认值兜底
     *
     * @param values       库内取值
     * @param key          参数名
     * @param defaultValue 默认值
     * @return 数值，非法返回 null
     */
    private BigDecimal decimal(Map<String, String> values, String key, String defaultValue) {
        String raw = values.get(key);
        String value = raw == null || raw.isBlank() ? defaultValue : raw;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.warn("指标参数不是合法数值 key={} value={}", key, value);
            return null;
        }
    }

    /**
     * 取开关字段，库值优先、默认值兜底
     *
     * @param values       库内取值
     * @param key          参数名
     * @param defaultValue 默认值
     * @return 布尔值，非法时按默认值处理
     */
    private Boolean flag(Map<String, String> values, String key, String defaultValue) {
        String raw = values.get(key);
        String value = raw == null || raw.isBlank() ? defaultValue : raw;
        if ("true".equalsIgnoreCase(value.trim())) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return Boolean.FALSE;
        }
        log.warn("指标开关不是合法布尔值 key={} value={}，按默认值处理", key, value);
        return Boolean.valueOf(defaultValue);
    }

    /**
     * 去掉多余尾零便于文案展示
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        BigDecimal trimmed = value.stripTrailingZeros();
        return trimmed.scale() < 0 ? trimmed.setScale(0, java.math.RoundingMode.UNNECESSARY).toPlainString()
                : trimmed.toPlainString();
    }
}
