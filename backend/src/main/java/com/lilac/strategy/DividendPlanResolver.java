package com.lilac.strategy;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lilac.constant.DividendParamKey;
import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.dto.param.ParamSaveRequest;
import com.lilac.domain.vo.StrategyParamVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.ParamGroup;
import com.lilac.service.StrategyParamService;
import com.lilac.strategy.model.DividendLadderRung;
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
 * 红利策略参数装配器，按标的读写 strategy_param 的 DIVIDEND 分组，值是一段阶梯 JSON
 */
@Slf4j
@Component
public class DividendPlanResolver {

    private static final int YIELD_SCALE = 2;
    private static final int AMOUNT_SCALE = 2;
    private static final BigDecimal MAX_YIELD = new BigDecimal("100");

    @Resource
    private StrategyParamService strategyParamService;

    /**
     * 读取指定标的的红利阶梯，没配过时返回空阶梯并由问题清单说明
     *
     * @param varietyKey  品种业务主键
     * @param varietyName 品种名称，可为 null
     * @return 参数，含问题清单
     */
    public DividendPlan resolveFor(String varietyKey, String varietyName) {
        ThrowUtils.throwIf(varietyKey == null || varietyKey.isBlank(), HttpsCodeEnum.PARAMS_ERROR,
                "必须指定要配置的标的");
        Map<String, String> values = readValues();
        DividendPlan plan = new DividendPlan();
        plan.setVarietyKey(varietyKey);
        plan.setVarietyName(varietyName);
        plan.setLadder(parse(values.get(DividendParamKey.keyOf(varietyKey))));
        if (plan.getLadder().isEmpty() && hasLegacyKeys(values)) {
            plan.getNotes().add("库内还留着旧版的单阈值方式（基准仓位 + 保底 + 两个阈值），"
                    + "已不再使用；请按阶梯重新设定这只基金的加减仓");
        }
        plan.setIssues(validate(plan));
        return plan;
    }

    /**
     * 列出全部已按标的配置过阶梯的红利基金，供页面逐只编辑
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
                    DividendPlan plan = new DividendPlan();
                    plan.setVarietyKey(key);
                    plan.setLadder(parse(values.get(DividendParamKey.keyOf(key))));
                    plan.setIssues(validate(plan));
                    byVariety.put(key, plan);
                });
        return new ArrayList<>(byVariety.values());
    }

    /**
     * 保存某只标的的红利阶梯，非法时拒绝保存并指出原因
     *
     * @param plan 待保存参数，必须带 varietyKey 与阶梯
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
        ParamSaveRequest request = new ParamSaveRequest();
        request.setParamGroup(ParamGroup.DIVIDEND);
        request.setParamKey(DividendParamKey.keyOf(varietyKey));
        request.setParamValue(toJson(plan.sortedLadder()));
        request.setParamDesc(DividendParamKey.LADDER_DESC);
        strategyParamService.saveParam(request);
        log.info("红利阶梯已保存 标的={} 档数={} 最高档持有={} 保底={}",
                varietyKey, plan.getLadder().size(), plan.topAmount(), plan.bottomAmount());
        return plan;
    }

    /**
     * 删除某只标的的红利阶梯，标的被清仓后不再参与建议时用它
     *
     * @param varietyKey 品种业务主键
     * @return 是否真的删掉了一行
     */
    public boolean remove(String varietyKey) {
        ThrowUtils.throwIf(varietyKey == null || varietyKey.isBlank(), HttpsCodeEnum.PARAMS_ERROR,
                "必须指定要清除的标的");
        boolean removed = strategyParamService.deleteParam(ParamGroup.DIVIDEND,
                DividendParamKey.keyOf(varietyKey.trim()));
        log.info("红利阶梯已清除 标的={} 删除={}", varietyKey, removed);
        return removed;
    }

    /**
     * 校验阶梯自洽性：至少两档、门槛递增、金额单调、最低档即保底
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
        List<DividendLadderRung> ladder = plan.sortedLadder();
        if (ladder.isEmpty()) {
            issues.add("股息率阶梯未设置，至少需要 " + DividendParamKey.MIN_RUNGS + " 档");
            return issues;
        }
        if (ladder.size() < DividendParamKey.MIN_RUNGS) {
            issues.add("股息率阶梯只有 " + ladder.size() + " 档，至少需要 "
                    + DividendParamKey.MIN_RUNGS + " 档，否则表达不了越跌越买");
        }
        if (ladder.size() > DividendParamKey.MAX_RUNGS) {
            issues.add("股息率阶梯最多 " + DividendParamKey.MAX_RUNGS + " 档，当前 " + ladder.size() + " 档");
        }
        BigDecimal previousYield = null;
        BigDecimal previousAmount = null;
        for (int index = 0; index < ladder.size(); index++) {
            DividendLadderRung rung = ladder.get(index);
            String label = "第 " + (index + 1) + " 档";
            if (rung.getYield() == null) {
                issues.add(label + "的股息率未填");
                continue;
            }
            if (rung.getYield().signum() <= 0 || rung.getYield().compareTo(MAX_YIELD) > 0) {
                issues.add(label + "的股息率应在 0~100 的百分点区间内，如 4.64 表示 4.64%");
            }
            if (rung.getAmount() == null) {
                issues.add(label + "的应持有金额未填");
                continue;
            }
            if (rung.getAmount().signum() <= 0) {
                issues.add(label + "的应持有金额必须大于 0");
            }
            // 阶梯按股息率降序排好，因此后一档的门槛必须严格更低、金额必须严格更少
            if (previousYield != null && rung.getYield().compareTo(previousYield) >= 0) {
                issues.add("阶梯里有重复的股息率 " + plain(rung.getYield()) + "%，同一门槛只能出现一次");
            }
            if (previousAmount != null && rung.getAmount().compareTo(previousAmount) > 0) {
                issues.add("股息率 " + plain(rung.getYield()) + "% 这一档的应持有金额 "
                        + plain(rung.getAmount()) + " 元高于更高股息率那一档的 " + plain(previousAmount)
                        + " 元；股息率越高应持有越多，不能反着来");
            }
            previousYield = rung.getYield();
            previousAmount = rung.getAmount();
        }
        return issues;
    }

    /**
     * 一次读回 DIVIDEND 分组的全部参数
     *
     * @return 参数名到参数值的映射
     */
    private Map<String, String> readValues() {
        ParamQueryRequest query = new ParamQueryRequest();
        query.setParamGroup(ParamGroup.DIVIDEND);
        Map<String, String> values = new LinkedHashMap<>();
        for (StrategyParamVO vo : strategyParamService.listParam(query)) {
            if (vo.getParamKey() != null && vo.getParamValue() != null) {
                values.put(vo.getParamKey(), vo.getParamValue());
            }
        }
        return values;
    }

    /**
     * 判断库里是否还留着旧版单阈值方式的键
     *
     * @param values 全部 DIVIDEND 参数
     * @return 存在旧键返回 true
     */
    private boolean hasLegacyKeys(Map<String, String> values) {
        return values.keySet().stream().anyMatch(key -> !key.contains(DividendParamKey.PER_VARIETY_SEPARATOR));
    }

    /**
     * 解析阶梯 JSON，格式非法时返回空列表交给校验说明
     *
     * @param raw JSON 文本
     * @return 阶梯，解析失败返回空列表
     */
    private List<DividendLadderRung> parse(String raw) {
        List<DividendLadderRung> ladder = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return ladder;
        }
        try {
            JSONArray array = JSONUtil.parseArray(raw);
            for (Object element : array) {
                if (!(element instanceof JSONObject object)) {
                    continue;
                }
                DividendLadderRung rung = new DividendLadderRung();
                rung.setYield(decimalOf(object.get("yield")));
                rung.setAmount(decimalOf(object.get("amount")));
                ladder.add(rung);
            }
        } catch (RuntimeException e) {
            // 库里是脏值时按未配置处理，不抛异常打断整页读取
            log.warn("红利阶梯不是合法 JSON，按未配置处理：{}", raw);
        }
        return ladder;
    }

    /**
     * 把阶梯写成 JSON，数值统一按固定小数位落库
     *
     * @param ladder 降序阶梯
     * @return JSON 文本
     */
    private String toJson(List<DividendLadderRung> ladder) {
        JSONArray array = new JSONArray();
        for (DividendLadderRung rung : ladder) {
            JSONObject object = new JSONObject();
            object.set("yield", rung.getYield().setScale(YIELD_SCALE, RoundingMode.HALF_UP));
            object.set("amount", rung.getAmount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
            array.add(object);
        }
        return array.toString();
    }

    /**
     * 把 JSON 里的数值转成 BigDecimal
     *
     * @param value 原始值
     * @return 数值，无法解析返回 null
     */
    private BigDecimal decimalOf(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
