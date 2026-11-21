package com.lilac.strategy;

import com.lilac.constant.DividendParamKey;
import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.dto.param.ParamSaveRequest;
import com.lilac.domain.vo.StrategyParamVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.ParamGroup;
import com.lilac.service.StrategyParamService;
import com.lilac.strategy.model.DividendPlan;
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
 * 红利策略参数装配器，按标的读写 strategy_param 的 DIVIDEND 分组并校验，兼容尚未拆分的全局旧键
 */
@Slf4j
@Component
public class DividendPlanResolver {

    private static final int PARAM_SCALE = 2;
    private static final BigDecimal MAX_YIELD = new BigDecimal("100");

    @Resource
    private StrategyParamService strategyParamService;

    /**
     * 读取指定标的的红利参数，该标的没配时回落到旧版完整参数并说明来源
     *
     * @param varietyKey  品种业务主键
     * @param varietyName 品种名称，可为 null
     * @return 参数，含问题清单
     */
    public DividendPlan resolveFor(String varietyKey, String varietyName) {
        ThrowUtils.throwIf(varietyKey == null || varietyKey.isBlank(), HttpsCodeEnum.PARAMS_ERROR,
                "必须指定要配置的标的");
        Map<String, String> values = readValues();
        DividendPlan plan = fill(values, varietyKey);
        plan.setVarietyKey(varietyKey);
        plan.setVarietyName(varietyName);
        if (isEmpty(plan)) {
            DividendPlan legacy = fill(values, null);
            legacy.setVarietyKey(varietyKey);
            legacy.setVarietyName(varietyName);
            // 旧键只作迁移兜底：必须四条齐全才算「真的配过」，半套旧值不参与，避免凭空长出一半参数
            if (legacy.complete()) {
                legacy.getNotes().add("该标的尚未单独配置，正在沿用旧版的全红利共用参数，请按这只基金重新设定");
                return legacy;
            }
            if (!isEmpty(legacy)) {
                plan.getNotes().add("库内还留着旧版的全红利共用参数，但未配全，本次不采用；请按这只基金重新设定");
            }
        }
        plan.setIssues(validate(plan));
        return plan;
    }

    /**
     * 列出全部已按标的配置过的红利参数，供页面逐只编辑
     *
     * @return 参数列表，按品种主键排序
     */
    public List<DividendPlan> listAll() {
        Map<String, String> values = readValues();
        Map<String, DividendPlan> byVariety = new LinkedHashMap<>();
        values.keySet().stream()
                .map(DividendParamKey::varietyKeyOf)
                .filter(key -> key != null)
                .distinct()
                .sorted()
                .forEach(key -> {
                    DividendPlan plan = fill(values, key);
                    plan.setVarietyKey(key);
                    plan.setIssues(validate(plan));
                    byVariety.put(key, plan);
                });
        return new ArrayList<>(byVariety.values());
    }

    /**
     * 保存某只标的的红利参数，非法时拒绝保存并指出字段
     *
     * @param plan 待保存参数，必须带 varietyKey
     * @return 保存后的参数
     */
    public DividendPlan save(DividendPlan plan) {
        ThrowUtils.throwIf(plan == null || plan.getVarietyKey() == null || plan.getVarietyKey().isBlank(),
                HttpsCodeEnum.PARAMS_ERROR, "必须指定要配置的标的");
        List<String> issues = validate(plan);
        plan.setIssues(issues);
        // 参数进了库就会长期参与建议，不合法时宁可拒绝保存也不让脏值落地
        ThrowUtils.throwIf(!issues.isEmpty(), HttpsCodeEnum.PARAMS_ERROR, String.join("；", issues));
        String varietyKey = plan.getVarietyKey().trim();
        writeParam(DividendParamKey.keyOf(varietyKey, DividendParamKey.BASE_AMOUNT), plan.getBaseAmount(),
                DividendParamKey.BASE_AMOUNT_DESC);
        writeParam(DividendParamKey.keyOf(varietyKey, DividendParamKey.FLOOR_AMOUNT), plan.getFloorAmount(),
                DividendParamKey.FLOOR_AMOUNT_DESC);
        writeParam(DividendParamKey.keyOf(varietyKey, DividendParamKey.ADD_YIELD), plan.getAddYield(),
                DividendParamKey.ADD_YIELD_DESC);
        writeParam(DividendParamKey.keyOf(varietyKey, DividendParamKey.REDUCE_YIELD), plan.getReduceYield(),
                DividendParamKey.REDUCE_YIELD_DESC);
        log.info("红利参数已保存 标的={} 基准={} 保底={} 加仓阈值={} 减仓阈值={}",
                varietyKey, plan.getBaseAmount(), plan.getFloorAmount(), plan.getAddYield(), plan.getReduceYield());
        return plan;
    }

    /**
     * 删除某只标的的红利参数，标的被清仓后不再参与建议时用它
     *
     * @param varietyKey 品种业务主键
     * @return 实际删除的条数
     */
    public int remove(String varietyKey) {
        ThrowUtils.throwIf(varietyKey == null || varietyKey.isBlank(), HttpsCodeEnum.PARAMS_ERROR,
                "必须指定要清除的标的");
        int removed = 0;
        for (String field : DividendParamKey.FIELDS) {
            if (strategyParamService.deleteParam(ParamGroup.DIVIDEND,
                    DividendParamKey.keyOf(varietyKey.trim(), field))) {
                removed++;
            }
        }
        log.info("红利参数已清除 标的={} 删除 {} 条", varietyKey, removed);
        return removed;
    }

    /**
     * 校验参数自洽性，金额与阈值都必须是正数且保底不得高于基准
     *
     * @param plan 参数
     * @return 问题清单，空表示可用
     */
    public List<String> validate(DividendPlan plan) {
        List<String> issues = new ArrayList<>();
        if (plan == null) {
            issues.add("红利参数不能为空");
            return issues;
        }
        checkAmount(issues, plan.getBaseAmount(), "基准仓位金额");
        checkAmount(issues, plan.getFloorAmount(), "保底分红金额");
        if (plan.getBaseAmount() != null && plan.getFloorAmount() != null
                && plan.getFloorAmount().compareTo(plan.getBaseAmount()) > 0) {
            // 保底高于基准意味着减仓目标比基准还大，任何减仓都会立刻越过基准，必须拒绝
            issues.add("保底分红金额 " + plain(plan.getFloorAmount()) + " 元不得高于基准仓位 "
                    + plain(plan.getBaseAmount()) + " 元");
        }
        checkYield(issues, plan.getAddYield(), "加仓股息率阈值");
        checkYield(issues, plan.getReduceYield(), "减仓股息率阈值");
        if (plan.getAddYield() != null && plan.getReduceYield() != null
                && plan.getReduceYield().compareTo(plan.getAddYield()) >= 0) {
            issues.add("减仓股息率阈值 " + plain(plan.getReduceYield()) + "% 必须低于加仓阈值 "
                    + plain(plan.getAddYield()) + "%");
        }
        return issues;
    }

    /**
     * 读库内全部 DIVIDEND 参数，同时包含按标的存放的键与全局旧键
     *
     * @return 参数名到参数值的映射
     */
    private Map<String, String> readValues() {
        ParamQueryRequest query = new ParamQueryRequest();
        query.setParamGroup(ParamGroup.DIVIDEND);
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
    private DividendPlan fill(Map<String, String> values, String varietyKey) {
        DividendPlan plan = new DividendPlan();
        plan.setBaseAmount(decimal(values.get(keyFor(varietyKey, DividendParamKey.BASE_AMOUNT))));
        plan.setFloorAmount(decimal(values.get(keyFor(varietyKey, DividendParamKey.FLOOR_AMOUNT))));
        plan.setAddYield(decimal(values.get(keyFor(varietyKey, DividendParamKey.ADD_YIELD))));
        plan.setReduceYield(decimal(values.get(keyFor(varietyKey, DividendParamKey.REDUCE_YIELD))));
        return plan;
    }

    /**
     * 是否一条参数都没读到
     *
     * @param plan 参数
     * @return 全为空返回 true
     */
    private boolean isEmpty(DividendPlan plan) {
        return plan.getBaseAmount() == null && plan.getFloorAmount() == null
                && plan.getAddYield() == null && plan.getReduceYield() == null;
    }

    /**
     * 按品种主键拼参数键，空主键时返回全局旧键
     *
     * @param varietyKey 品种业务主键
     * @param field      字段名
     * @return 参数键
     */
    private String keyFor(String varietyKey, String field) {
        return varietyKey == null ? field : DividendParamKey.keyOf(varietyKey, field);
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
            log.warn("红利参数不是合法数值：{}", raw);
            return null;
        }
    }

    /**
     * 校验金额字段
     *
     * @param issues 问题清单
     * @param amount 金额
     * @param label  字段中文名
     */
    private void checkAmount(List<String> issues, BigDecimal amount, String label) {
        if (amount == null) {
            issues.add(label + "未设置");
            return;
        }
        if (amount.signum() <= 0) {
            issues.add(label + "必须大于 0");
        }
    }

    /**
     * 校验股息率阈值字段，百分点口径下上限取 100
     *
     * @param issues 问题清单
     * @param yield  股息率阈值
     * @param label  字段中文名
     */
    private void checkYield(List<String> issues, BigDecimal yield, String label) {
        if (yield == null) {
            issues.add(label + "未设置");
            return;
        }
        if (yield.signum() <= 0 || yield.compareTo(MAX_YIELD) > 0) {
            issues.add(label + "应在 0~100 的百分点区间内，如 4.08 表示 4.08%");
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
        request.setParamGroup(ParamGroup.DIVIDEND);
        request.setParamKey(key);
        request.setParamValue(value.setScale(PARAM_SCALE, RoundingMode.HALF_UP).toPlainString());
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
