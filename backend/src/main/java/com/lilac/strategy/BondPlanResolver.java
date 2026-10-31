package com.lilac.strategy;

import com.lilac.constant.BondParamKey;
import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.dto.param.ParamSaveRequest;
import com.lilac.domain.vo.StrategyParamVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.ParamGroup;
import com.lilac.service.StrategyParamService;
import com.lilac.strategy.model.BondPlan;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 债券监控参数装配器，按 strategy_param 的 BOND 分组读写并做自洽校验
 */
@Slf4j
@Component
public class BondPlanResolver {

    private static final int MIN_WINDOW = 10;
    private static final int MAX_WINDOW = 250;

    @Resource
    private StrategyParamService strategyParamService;

    /**
     * 读取债券监控参数，库里没有的字段回落到内置默认值
     *
     * @return 参数，含问题清单
     */
    public BondPlan resolve() {
        Map<String, String> values = readValues();
        BondPlan plan = new BondPlan();
        plan.setWindow(integer(values, BondParamKey.WINDOW, BondParamKey.DEFAULT_WINDOW));
        plan.setSidewaysBand(decimal(values, BondParamKey.SIDEWAYS_BAND, BondParamKey.DEFAULT_SIDEWAYS_BAND));
        plan.setSidewaysDays(integer(values, BondParamKey.SIDEWAYS_DAYS, BondParamKey.DEFAULT_SIDEWAYS_DAYS));
        plan.setSidewaysReturn(decimal(values, BondParamKey.SIDEWAYS_RETURN, BondParamKey.DEFAULT_SIDEWAYS_RETURN));
        plan.setWeakSlope(decimal(values, BondParamKey.WEAK_SLOPE, BondParamKey.DEFAULT_WEAK_SLOPE));
        plan.setWeakVolatility(decimal(values, BondParamKey.WEAK_VOLATILITY, BondParamKey.DEFAULT_WEAK_VOLATILITY));
        plan.setIssues(validate(plan));
        return plan;
    }

    /**
     * 保存债券监控参数，非法时拒绝保存并指出字段
     *
     * @param plan 待保存参数
     * @return 保存后的参数
     */
    public BondPlan save(BondPlan plan) {
        ThrowUtils.throwIf(plan == null, HttpsCodeEnum.PARAMS_ERROR, "债券监控参数不能为空");
        List<String> issues = validate(plan);
        plan.setIssues(issues);
        // 参数进了库就会长期参与提醒，不合法时宁可拒绝保存也不让脏值落地
        ThrowUtils.throwIf(!issues.isEmpty(), HttpsCodeEnum.PARAMS_ERROR, String.join("；", issues));
        writeParam(BondParamKey.WINDOW, String.valueOf(plan.getWindow()), BondParamKey.WINDOW_DESC);
        writeParam(BondParamKey.SIDEWAYS_BAND, plain(plan.getSidewaysBand()), BondParamKey.SIDEWAYS_BAND_DESC);
        writeParam(BondParamKey.SIDEWAYS_DAYS, String.valueOf(plan.getSidewaysDays()), BondParamKey.SIDEWAYS_DAYS_DESC);
        writeParam(BondParamKey.SIDEWAYS_RETURN, plain(plan.getSidewaysReturn()),
                BondParamKey.SIDEWAYS_RETURN_DESC);
        writeParam(BondParamKey.WEAK_SLOPE, plain(plan.getWeakSlope()), BondParamKey.WEAK_SLOPE_DESC);
        writeParam(BondParamKey.WEAK_VOLATILITY, plain(plan.getWeakVolatility()), BondParamKey.WEAK_VOLATILITY_DESC);
        log.info("债券监控参数已保存 窗口={} 横盘带宽={} 横盘天数={} 横盘涨幅上限={} 转弱斜率={} 转弱波动={}",
                plan.getWindow(), plan.getSidewaysBand(), plan.getSidewaysDays(), plan.getSidewaysReturn(),
                plan.getWeakSlope(), plan.getWeakVolatility());
        return plan;
    }

    /**
     * 校验参数自洽性，窗口须能覆盖横盘天数，阈值须为正
     *
     * @param plan 参数
     * @return 问题清单，空表示可用
     */
    public List<String> validate(BondPlan plan) {
        List<String> issues = new ArrayList<>();
        if (plan == null) {
            issues.add("债券监控参数不能为空");
            return issues;
        }
        Integer window = plan.getWindow();
        if (window == null || window < MIN_WINDOW) {
            issues.add("回看窗口至少要 " + MIN_WINDOW + " 个交易日，否则斜率的噪声会盖过趋势");
        } else if (window > MAX_WINDOW) {
            issues.add("回看窗口不应超过 " + MAX_WINDOW + " 个交易日");
        }
        Integer days = plan.getSidewaysDays();
        if (days == null || days <= 0) {
            issues.add("横盘天数必须大于 0");
        } else if (window != null && days > window) {
            // 横盘天数超出回看窗口时永远触发不了，属于自相矛盾的配置
            issues.add("横盘天数 " + days + " 不得超过回看窗口 " + window + " 个交易日");
        }
        checkPositive(issues, plan.getSidewaysBand(), "单日波动上限");
        checkPositive(issues, plan.getWeakVolatility(), "转弱波动率");
        if (plan.getSidewaysBand() != null && plan.getWeakVolatility() != null
                && plan.getWeakVolatility().compareTo(plan.getSidewaysBand()) < 0) {
            issues.add("转弱波动率 " + plain(plan.getWeakVolatility()) + "% 不应低于横盘带宽 "
                    + plain(plan.getSidewaysBand()) + "%，否则任何横盘都会被判成转弱");
        }
        if (plan.getSidewaysReturn() == null) {
            issues.add("横盘涨幅上限未设置");
        }
        if (plan.getWeakSlope() == null) {
            issues.add("转弱斜率未设置");
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
     * 读库内全部 BOND 参数
     *
     * @return 参数名到参数值的映射
     */
    private Map<String, String> readValues() {
        ParamQueryRequest query = new ParamQueryRequest();
        query.setParamGroup(ParamGroup.BOND);
        Map<String, String> values = new LinkedHashMap<>();
        for (StrategyParamVO vo : strategyParamService.listParam(query)) {
            if (vo.getParamKey() != null) {
                values.put(vo.getParamKey(), vo.getParamValue());
            }
        }
        return values;
    }

    /**
     * 按「分组 + 参数名」写一条参数，存在则更新
     *
     * @param key   参数名
     * @param value 参数值
     * @param desc  参数说明
     */
    private void writeParam(String key, String value, String desc) {
        ParamSaveRequest request = new ParamSaveRequest();
        request.setParamGroup(ParamGroup.BOND);
        request.setParamKey(key);
        request.setParamValue(value);
        request.setParamDesc(desc);
        strategyParamService.saveParam(request);
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
            log.warn("债券监控参数不是合法整数 key={} value={}", key, value);
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
            log.warn("债券监控参数不是合法数值 key={} value={}", key, value);
            return null;
        }
    }

    /**
     * 去掉多余尾零便于落库与文案展示
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal trimmed = value.stripTrailingZeros();
        return trimmed.scale() < 0 ? trimmed.setScale(0, java.math.RoundingMode.UNNECESSARY).toPlainString()
                : trimmed.toPlainString();
    }
}
