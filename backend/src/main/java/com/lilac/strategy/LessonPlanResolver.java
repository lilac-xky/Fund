package com.lilac.strategy;

import com.lilac.constant.LessonParamKey;
import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.vo.StrategyParamVO;
import com.lilac.enums.ParamGroup;
import com.lilac.service.StrategyParamService;
import com.lilac.strategy.model.LessonPlan;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史教训警示参数装配器，读取 strategy_param 的 ALERT 分组并校验三类模式的阈值
 */
@Slf4j
@Component
public class LessonPlanResolver {

    @Resource
    private StrategyParamService strategyParamService;

    /**
     * 读取警示参数，库里没有的字段回落到内置默认值
     *
     * @return 参数，含问题清单
     */
    public LessonPlan resolve() {
        Map<String, String> values = readValues();
        LessonPlan plan = new LessonPlan();
        plan.setChaseTolerance(decimal(values, LessonParamKey.CHASE_TOLERANCE,
                LessonParamKey.DEFAULT_CHASE_TOLERANCE));
        plan.setChaseWindow(integer(values, LessonParamKey.CHASE_WINDOW, LessonParamKey.DEFAULT_CHASE_WINDOW));
        plan.setPanicLossDays(integer(values, LessonParamKey.PANIC_LOSS_DAYS,
                LessonParamKey.DEFAULT_PANIC_LOSS_DAYS));
        plan.setPanicLossRate(decimal(values, LessonParamKey.PANIC_LOSS_RATE,
                LessonParamKey.DEFAULT_PANIC_LOSS_RATE));
        plan.setFamiliarDays(integer(values, LessonParamKey.FAMILIAR_DAYS, LessonParamKey.DEFAULT_FAMILIAR_DAYS));
        plan.setParticipateInAlert(flag(values, LessonParamKey.PARTICIPATE_IN_ALERT,
                LessonParamKey.DEFAULT_PARTICIPATE_IN_ALERT));
        plan.setIssues(validate(plan));
        return plan;
    }

    /**
     * 校验参数自洽性，阈值须为正、天数须大于 0
     *
     * @param plan 参数
     * @return 问题清单，空表示可用
     */
    public List<String> validate(LessonPlan plan) {
        List<String> issues = new ArrayList<>();
        if (plan == null) {
            issues.add("历史教训参数不能为空");
            return issues;
        }
        checkPositive(issues, plan.getChaseTolerance(), "追高容忍幅度");
        checkPositive(issues, plan.getPanicLossRate(), "累计亏损率下限");
        checkDays(issues, plan.getChaseWindow(), "追高回看窗口", 2);
        checkDays(issues, plan.getPanicLossDays(), "连续亏损天数", 1);
        checkDays(issues, plan.getFamiliarDays(), "熟悉品种天数", 1);
        if (plan.getChaseTolerance() != null && plan.getChaseTolerance().compareTo(BigDecimal.valueOf(100)) > 0) {
            issues.add("追高容忍幅度不应超过 100%，否则任何买入都会被判为追高");
        }
        return issues;
    }

    /**
     * 校验字段为正值
     *
     * @param issues 问题清单
     * @param value  数值
     * @param label  字段中文名
     */
    private void checkPositive(List<String> issues, BigDecimal value, String label) {
        if (value == null) {
            issues.add(label + "未设置");
            return;
        }
        if (value.signum() <= 0) {
            issues.add(label + "必须大于 0");
        }
    }

    /**
     * 校验天数字段下限
     *
     * @param issues 问题清单
     * @param value  天数
     * @param label  字段中文名
     * @param min    最小值
     */
    private void checkDays(List<String> issues, Integer value, String label, int min) {
        if (value == null) {
            issues.add(label + "未设置");
            return;
        }
        if (value < min) {
            issues.add(label + "至少为 " + min + " 天");
        }
    }

    /**
     * 读库内全部 ALERT 参数
     *
     * @return 参数名到参数值的映射
     */
    private Map<String, String> readValues() {
        ParamQueryRequest query = new ParamQueryRequest();
        query.setParamGroup(ParamGroup.ALERT);
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
            log.warn("历史教训参数不是合法整数 key={} value={}", key, value);
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
            log.warn("历史教训参数不是合法数值 key={} value={}", key, value);
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
        log.warn("历史教训开关不是合法布尔值 key={} value={}，按默认值处理", key, value);
        return Boolean.valueOf(defaultValue);
    }
}
