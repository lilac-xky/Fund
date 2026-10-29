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
 * 网格策略参数装配器，按标的读写 strategy_param 的 GRID 分组，并兼容尚未拆分的全局旧键
 */
@Slf4j
@Component
public class GridPlanResolver {

    private static final int PARAM_SCALE = 4;
    private static final int MIN_GRIDS = 2;

    @Resource
    private StrategyParamService strategyParamService;

    /**
     * 读取全局旧参数，供尚未按标的配置时的兜底与迁移提示
     *
     * @return 参数，含问题清单
     */
    public GridPlan resolve() {
        GridPlan plan = fill(readValues(), null);
        plan.setIssues(validate(plan));
        return plan;
    }

    /**
     * 读取指定标的的网格参数，该标的没配时回落到全局旧参数并说明来源
     *
     * @param varietyKey 品种业务主键
     * @param varietyName 品种名称，可为 null
     * @return 参数，含问题清单
     */
    public GridPlan resolveFor(String varietyKey, String varietyName) {
        Map<String, String> values = readValues();
        GridPlan plan = fill(values, varietyKey);
        plan.setVarietyKey(varietyKey);
        plan.setVarietyName(varietyName);
        if (plan.getLowerBound() == null && plan.getUpperBound() == null
                && plan.getStep() == null && plan.getAmountPerGrid() == null) {
            // 该标的还没配过，用全局旧参数顶上，并明确告诉使用者这是共用的
            plan = fill(values, null);
            plan.setVarietyKey(varietyKey);
            plan.setVarietyName(varietyName);
            plan.setIssues(validate(plan));
            if (plan.complete()) {
                // 提示进 notes 而不是 issues，否则会被当成参数不可用
                plan.getNotes().add("该标的尚未单独配置，正在沿用未分标的的通用网格参数");
            }
            return plan;
        }
        plan.setIssues(validate(plan));
        return plan;
    }

    /**
     * 列出全部已单独配置过的网格参数，供页面逐只编辑
     *
     * @return 参数列表，按品种主键排序
     */
    public List<GridPlan> listAll() {
        Map<String, String> values = readValues();
        Map<String, GridPlan> byVariety = new LinkedHashMap<>();
        values.keySet().stream()
                .map(GridParamKey::varietyKeyOf)
                .filter(key -> key != null)
                .distinct()
                .sorted()
                .forEach(key -> {
                    GridPlan plan = fill(values, key);
                    plan.setVarietyKey(key);
                    plan.setIssues(validate(plan));
                    byVariety.put(key, plan);
                });
        return new ArrayList<>(byVariety.values());
    }

    /**
     * 保存某只标的的网格参数，非法时拒绝保存并指出字段
     *
     * @param plan 待保存参数，必须带 varietyKey
     * @return 保存后的参数
     */
    public GridPlan save(GridPlan plan) {
        ThrowUtils.throwIf(plan == null || plan.getVarietyKey() == null
                || plan.getVarietyKey().isBlank(), HttpsCodeEnum.PARAMS_ERROR, "必须指定要配置的标的");
        List<String> issues = validate(plan);
        plan.setIssues(issues);
        // 参数进了库就会长期参与提醒，不合法时宁可拒绝保存也不让脏值落地
        ThrowUtils.throwIf(!issues.isEmpty(), HttpsCodeEnum.PARAMS_ERROR, String.join("；", issues));
        String varietyKey = plan.getVarietyKey().trim();
        writeParam(GridParamKey.keyOf(varietyKey, GridParamKey.LOWER_BOUND), plan.getLowerBound(),
                GridParamKey.LOWER_BOUND_DESC);
        writeParam(GridParamKey.keyOf(varietyKey, GridParamKey.UPPER_BOUND), plan.getUpperBound(),
                GridParamKey.UPPER_BOUND_DESC);
        writeParam(GridParamKey.keyOf(varietyKey, GridParamKey.STEP), plan.getStep(),
                GridParamKey.STEP_DESC);
        writeParam(GridParamKey.keyOf(varietyKey, GridParamKey.AMOUNT_PER_GRID), plan.getAmountPerGrid(),
                GridParamKey.AMOUNT_PER_GRID_DESC);
        log.info("网格参数已保存 标的={} 下界={} 上界={} 格子大小={} 每格金额={}",
                varietyKey, plan.getLowerBound(), plan.getUpperBound(), plan.getStep(), plan.getAmountPerGrid());
        return plan;
    }

    /**
     * 保存全局旧参数，保留给尚未迁移的调用方与参数页的兼容入口
     *
     * @param plan 待保存参数
     * @return 保存后的参数
     */
    public GridPlan saveGlobal(GridPlan plan) {
        List<String> issues = validate(plan);
        plan.setIssues(issues);
        ThrowUtils.throwIf(!issues.isEmpty(), HttpsCodeEnum.PARAMS_ERROR, String.join("；", issues));
        writeParam(GridParamKey.LOWER_BOUND, plan.getLowerBound(), GridParamKey.LOWER_BOUND_DESC);
        writeParam(GridParamKey.UPPER_BOUND, plan.getUpperBound(), GridParamKey.UPPER_BOUND_DESC);
        writeParam(GridParamKey.STEP, plan.getStep(), GridParamKey.STEP_DESC);
        writeParam(GridParamKey.AMOUNT_PER_GRID, plan.getAmountPerGrid(), GridParamKey.AMOUNT_PER_GRID_DESC);
        log.info("通用网格参数已保存 下界={} 上界={} 格子大小={} 每格金额={}",
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
     * 读库内全部 GRID 参数，同时包含全局旧键与按标的存放的键
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
     * 把字符串参数转成数值，品种主键为空时读全局旧键
     *
     * @param values     参数映射
     * @param varietyKey 品种业务主键，为 null 读全局旧键
     * @return 参数
     */
    private GridPlan fill(Map<String, String> values, String varietyKey) {
        GridPlan plan = new GridPlan();
        plan.setLowerBound(decimal(values.get(keyFor(varietyKey, GridParamKey.LOWER_BOUND))));
        plan.setUpperBound(decimal(values.get(keyFor(varietyKey, GridParamKey.UPPER_BOUND))));
        plan.setStep(decimal(values.get(keyFor(varietyKey, GridParamKey.STEP))));
        plan.setAmountPerGrid(decimal(values.get(keyFor(varietyKey, GridParamKey.AMOUNT_PER_GRID))));
        return plan;
    }

    /**
     * 按品种主键拼参数键，空主键时返回全局旧键
     *
     * @param varietyKey 品种业务主键
     * @param field      字段名
     * @return 参数键
     */
    private String keyFor(String varietyKey, String field) {
        return varietyKey == null ? field : GridParamKey.keyOf(varietyKey, field);
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
