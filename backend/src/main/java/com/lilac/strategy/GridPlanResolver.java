package com.lilac.strategy;

import com.lilac.constant.GridParamKey;
import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.dto.param.ParamSaveRequest;
import com.lilac.domain.vo.StrategyParamVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.ParamGroup;
import com.lilac.service.StrategyParamService;
import com.lilac.strategy.model.GridPlan;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网格策略参数装配器，负责从 strategy_param 读值、校验并识别不合法组合
 */
@Slf4j
@Component
public class GridPlanResolver {

    private static final int PARAM_SCALE = 4;
    private static final int MIN_GRIDS = 2;

    @Resource
    private StrategyParamService strategyParamService;

    /**
     * 读取并校验当前网格参数，缺项与非法值都记入问题清单
     *
     * @return 参数，含问题清单
     */
    public GridPlan resolve() {
        GridPlan plan = fill(readValues());
        plan.setIssues(validate(plan));
        return plan;
    }

    /**
     * 保存一组网格参数，非法时拒绝保存并指出字段
     *
     * @param plan 待保存参数
     * @return 保存后的参数
     */
    public GridPlan save(GridPlan plan) {
        List<String> issues = validate(plan);
        plan.setIssues(issues);
        // 参数进了库就会长期参与提醒，不合法时宁可拒绝保存也不让脏值落地
        ThrowUtils.throwIf(!issues.isEmpty(), HttpsCodeEnum.PARAMS_ERROR, String.join("；", issues));
        writeParam(GridParamKey.LOWER_BOUND, plan.getLowerBound(), GridParamKey.LOWER_BOUND_DESC);
        writeParam(GridParamKey.UPPER_BOUND, plan.getUpperBound(), GridParamKey.UPPER_BOUND_DESC);
        writeParam(GridParamKey.STEP, plan.getStep(), GridParamKey.STEP_DESC);
        writeParam(GridParamKey.AMOUNT_PER_GRID, plan.getAmountPerGrid(), GridParamKey.AMOUNT_PER_GRID_DESC);
        log.info("网格参数已保存 下界={} 上界={} 格子大小={} 每格金额={}",
                plan.getLowerBound(), plan.getUpperBound(), plan.getStep(), plan.getAmountPerGrid());
        return plan;
    }

    /**
     * 校验参数自洽性，上下界必须正且上界大于下界，格子大小要能划出至少两格
     *
     * @param plan 参数
     * @return 问题清单，空表示可用
     */
    public List<String> validate(GridPlan plan) {
        List<String> issues = new ArrayList<>();
        if (plan == null) {
            issues.add("网格参数不能为空");
            return issues;
        }
        checkPositive(issues, plan.getLowerBound(), "价格下界");
        checkPositive(issues, plan.getUpperBound(), "价格上界");
        checkPositive(issues, plan.getStep(), "格子大小");
        checkPositive(issues, plan.getAmountPerGrid(), "每格金额");
        if (plan.getLowerBound() != null && plan.getUpperBound() != null
                && plan.getUpperBound().compareTo(plan.getLowerBound()) <= 0) {
            issues.add("价格上界 " + plain(plan.getUpperBound()) + " 元必须大于下界 "
                    + plain(plan.getLowerBound()) + " 元");
        }
        if (plan.getLowerBound() != null && plan.getUpperBound() != null && plan.getStep() != null
                && plan.getUpperBound().compareTo(plan.getLowerBound()) > 0 && plan.getStep().signum() > 0) {
            int grids = plan.getUpperBound().subtract(plan.getLowerBound())
                    .divide(plan.getStep(), 0, RoundingMode.DOWN).intValue();
            if (grids < MIN_GRIDS) {
                // 只划得出一格时网格退化成一个价格点，触发逻辑会变成噪声
                issues.add("按当前上下界与格子大小只能划出 " + grids + " 格，至少需要 " + MIN_GRIDS + " 格");
            }
        }
        return issues;
    }

    /**
     * 读库内四条网格参数
     *
     * @return 参数名到参数值的映射
     */
    private Map<String, String> readValues() {
        ParamQueryRequest query = new ParamQueryRequest();
        query.setParamGroup(ParamGroup.GRID);
        Map<String, String> values = new LinkedHashMap<>();
        for (StrategyParamVO vo : strategyParamService.listParam(query)) {
            if (vo.getParamKey() != null) {
                values.put(vo.getParamKey(), vo.getParamValue());
            }
        }
        return values;
    }

    /**
     * 把字符串参数转成数值，无法解析时留空交给校验说明
     *
     * @param values 参数映射
     * @return 参数
     */
    private GridPlan fill(Map<String, String> values) {
        GridPlan plan = new GridPlan();
        plan.setLowerBound(decimal(values.get(GridParamKey.LOWER_BOUND)));
        plan.setUpperBound(decimal(values.get(GridParamKey.UPPER_BOUND)));
        plan.setStep(decimal(values.get(GridParamKey.STEP)));
        plan.setAmountPerGrid(decimal(values.get(GridParamKey.AMOUNT_PER_GRID)));
        return plan;
    }

    /**
     * 解析十进制数值，空值与非法串都返回 null
     *
     * @param raw 原始字符串
     * @return 数值，无法解析返回 null
     */
    private BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("网格参数不是合法数值：{}", raw);
            return null;
        }
    }

    /**
     * 校验正数字段
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
     * 按「分组 + 参数名」写一条参数，存在则更新
     *
     * @param key   参数名
     * @param value 参数值
     * @param desc  参数说明
     */
    private void writeParam(String key, BigDecimal value, String desc) {
        ParamSaveRequest request = new ParamSaveRequest();
        request.setParamGroup(ParamGroup.GRID);
        request.setParamKey(key);
        request.setParamValue(value.setScale(PARAM_SCALE, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        request.setParamDesc(desc);
        strategyParamService.saveParam(request);
    }

    /**
     * 去掉多余尾零便于文案展示
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        BigDecimal trimmed = value.stripTrailingZeros();
        return trimmed.scale() < 0 ? trimmed.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
                : trimmed.toPlainString();
    }
}
