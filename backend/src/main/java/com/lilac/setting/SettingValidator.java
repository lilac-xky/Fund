package com.lilac.setting;

import com.lilac.constant.AlertParamKey;
import com.lilac.constant.BondParamKey;
import com.lilac.constant.DividendParamKey;
import com.lilac.constant.GridParamKey;
import com.lilac.constant.IndicatorParamKey;
import com.lilac.constant.SettingCatalog;
import com.lilac.domain.dto.setting.SettingFieldError;
import com.lilac.domain.dto.setting.SettingGroupSaveRequest;
import com.lilac.enums.ParamGroup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数校验器，逐字段校验并顺带做跨字段的自洽检查，错误按字段返回而非一句总述
 */
@Component
public class SettingValidator {

    /**
     * 股息率阈值的百分点上限
     */
    private static final BigDecimal MAX_YIELD = new BigDecimal("100");

    /**
     * RSI 阈值的上限
     */
    private static final BigDecimal MAX_RSI = new BigDecimal("100");

    /**
     * 校验整批提交，跨字段规则在合并后的视图上判断，避免只改一半时误报
     *
     * @param requests 待保存的分组
     * @param current  库内当前值，键形如 DIVIDEND.baseAmount
     * @return 字段错误，空表示通过
     */
    public List<SettingFieldError> validate(List<SettingGroupSaveRequest> requests, Map<String, String> current) {
        List<SettingFieldError> errors = new ArrayList<>();
        Map<String, String> merged = new LinkedHashMap<>(current);
        for (SettingGroupSaveRequest request : requests) {
            ParamGroup group = parseGroup(request.getGroup());
            if (group == null) {
                errors.add(SettingFieldError.of(request.getGroup(), "", "分组", "未知的参数分组"));
                continue;
            }
            if (!SettingFieldCatalog.writable(group)) {
                errors.add(SettingFieldError.of(request.getGroup(), "", "分组",
                        "该分组不支持通过参数表保存，SMTP 相关请走邮件设置"));
                continue;
            }
            if (request.getValues() == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : request.getValues().entrySet()) {
                SettingFieldCatalog.FieldDef def = SettingFieldCatalog.find(group, entry.getKey());
                if (def == null) {
                    errors.add(SettingFieldError.of(request.getGroup(), entry.getKey(), entry.getKey(),
                            "不是本分组支持的参数"));
                    continue;
                }
                checkField(errors, def, entry.getValue());
                if (entry.getValue() != null) {
                    merged.put(group.name() + "." + entry.getKey(), entry.getValue().trim());
                }
            }
        }
        crossCheck(errors, merged);
        return errors;
    }

    /**
     * 单字段校验，类型、正负与上限都在这里判定
     *
     * @param errors 错误清单
     * @param def    字段定义
     * @param raw    原始值
     */
    private void checkField(List<SettingFieldError> errors, SettingFieldCatalog.FieldDef def, String raw) {
        String value = raw == null ? null : raw.trim();
        if (value == null || value.isEmpty()) {
            errors.add(error(def, "不能为空"));
            return;
        }
        if (SettingCatalog.TYPE_BOOLEAN.equals(def.getType())) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                errors.add(error(def, "只能是开启或关闭"));
            }
            return;
        }
        if (SettingCatalog.TYPE_INTEGER.equals(def.getType())) {
            Integer number = integer(value);
            if (number == null) {
                errors.add(error(def, "必须是整数"));
                return;
            }
            if (number <= 0) {
                errors.add(error(def, "必须大于 0"));
                return;
            }
            checkIntegerRange(errors, def, number);
            return;
        }
        BigDecimal number = decimal(value);
        if (number == null) {
            errors.add(error(def, "必须是数字"));
            return;
        }
        checkDecimalRange(errors, def, number);
    }

    /**
     * 金额与阈值的数值范围校验
     *
     * @param errors 错误清单
     * @param def    字段定义
     * @param number 数值
     */
    private void checkDecimalRange(List<SettingFieldError> errors, SettingFieldCatalog.FieldDef def, BigDecimal number) {
        if (number.signum() <= 0) {
            errors.add(error(def, "必须大于 0"));
            return;
        }
        if (DividendParamKey.ADD_YIELD.equals(def.getKey()) || DividendParamKey.REDUCE_YIELD.equals(def.getKey())) {
            if (number.compareTo(MAX_YIELD) > 0) {
                errors.add(error(def, "应在 0~100 的百分点区间内，如 4.08 表示 4.08%"));
            }
            return;
        }
        if (IndicatorParamKey.RSI_UPPER.equals(def.getKey()) || IndicatorParamKey.RSI_LOWER.equals(def.getKey())) {
            if (number.compareTo(MAX_RSI) > 0) {
                errors.add(error(def, "应在 0~100 之间"));
            }
        }
    }

    /**
     * 整数型字段的取值范围，周期类不能太小否则算不出指标
     *
     * @param errors 错误清单
     * @param def    字段定义
     * @param number 数值
     */
    private void checkIntegerRange(List<SettingFieldError> errors, SettingFieldCatalog.FieldDef def, Integer number) {
        boolean period = IndicatorParamKey.RSI_PERIOD.equals(def.getKey())
                || IndicatorParamKey.KDJ_PERIOD.equals(def.getKey())
                || IndicatorParamKey.MACD_FAST.equals(def.getKey())
                || IndicatorParamKey.MACD_SLOW.equals(def.getKey())
                || IndicatorParamKey.MACD_SIGNAL.equals(def.getKey());
        if (period && number < 2) {
            errors.add(error(def, "周期至少为 2 日，否则指标无意义"));
        }
    }

    /**
     * 跨字段自洽校验，依赖合并后的最终取值，所以放在全部单字段校验之后
     *
     * @param errors 错误清单
     * @param merged 合并后的取值
     */
    private void crossCheck(List<SettingFieldError> errors, Map<String, String> merged) {
        String dividend = ParamGroup.DIVIDEND.name();
        BigDecimal base = decimal(merged.get(dividend + "." + DividendParamKey.BASE_AMOUNT));
        BigDecimal floor = decimal(merged.get(dividend + "." + DividendParamKey.FLOOR_AMOUNT));
        if (base != null && floor != null && floor.compareTo(base) > 0) {
            // 保底高于基准意味着任何减仓都会立刻突破护城河，必须拒绝
            errors.add(SettingFieldError.of(dividend, DividendParamKey.FLOOR_AMOUNT, "保底分红金额",
                    "保底分红金额 " + plain(floor) + " 元不得高于基准仓位 " + plain(base) + " 元"));
        }
        BigDecimal addYield = decimal(merged.get(dividend + "." + DividendParamKey.ADD_YIELD));
        BigDecimal reduceYield = decimal(merged.get(dividend + "." + DividendParamKey.REDUCE_YIELD));
        if (addYield != null && reduceYield != null && reduceYield.compareTo(addYield) >= 0) {
            errors.add(SettingFieldError.of(dividend, DividendParamKey.REDUCE_YIELD, "减仓股息率阈值",
                    "减仓股息率阈值 " + plain(reduceYield) + "% 必须低于加仓阈值 " + plain(addYield) + "%"));
        }

        String grid = ParamGroup.GRID.name();
        BigDecimal lower = decimal(merged.get(grid + "." + GridParamKey.LOWER_BOUND));
        BigDecimal upper = decimal(merged.get(grid + "." + GridParamKey.UPPER_BOUND));
        BigDecimal step = decimal(merged.get(grid + "." + GridParamKey.STEP));
        if (lower != null && upper != null && lower.compareTo(upper) >= 0) {
            errors.add(SettingFieldError.of(grid, GridParamKey.UPPER_BOUND, "价格上界",
                    "价格上界必须大于下界 " + plain(lower) + " 元"));
        } else if (lower != null && upper != null && step != null) {
            BigDecimal span = upper.subtract(lower);
            int grids = span.divide(step, 0, java.math.RoundingMode.DOWN).intValue();
            if (grids < 2) {
                errors.add(SettingFieldError.of(grid, GridParamKey.STEP, "格子大小",
                        "按当前上下界与格子大小只能划出 " + grids + " 格，至少需要 2 格"));
            }
        }

        String bond = ParamGroup.BOND.name();
        Integer bondWindow = integer(merged.get(bond + "." + BondParamKey.WINDOW));
        Integer bondDays = integer(merged.get(bond + "." + BondParamKey.SIDEWAYS_DAYS));
        if (bondWindow != null && bondDays != null && bondDays > bondWindow) {
            // 横盘天数超过回看窗口时永远触发不了，属于自相矛盾的配置
            errors.add(SettingFieldError.of(bond, BondParamKey.SIDEWAYS_DAYS, "横盘天数阈值",
                    "横盘天数 " + bondDays + " 不得超过回看窗口 " + bondWindow + " 个交易日"));
        }
        BigDecimal bondBand = decimal(merged.get(bond + "." + BondParamKey.SIDEWAYS_BAND));
        BigDecimal bondVolatility = decimal(merged.get(bond + "." + BondParamKey.WEAK_VOLATILITY));
        if (bondBand != null && bondVolatility != null && bondVolatility.compareTo(bondBand) < 0) {
            errors.add(SettingFieldError.of(bond, BondParamKey.WEAK_VOLATILITY, "转弱波动率下限",
                    "转弱波动率 " + plain(bondVolatility) + "% 不应低于横盘带宽 " + plain(bondBand) + "%"));
        }

        String indicator = ParamGroup.INDICATOR.name();
        Integer fast = integer(merged.get(indicator + "." + IndicatorParamKey.MACD_FAST));
        Integer slow = integer(merged.get(indicator + "." + IndicatorParamKey.MACD_SLOW));
        if (fast != null && slow != null && fast >= slow) {
            errors.add(SettingFieldError.of(indicator, IndicatorParamKey.MACD_SLOW, "MACD 慢线周期",
                    "慢线周期必须大于快线周期 " + fast));
        }
        Integer signal = integer(merged.get(indicator + "." + IndicatorParamKey.MACD_SIGNAL));
        if (signal != null && slow != null && signal > slow) {
            errors.add(SettingFieldError.of(indicator, IndicatorParamKey.MACD_SIGNAL, "MACD 信号线周期",
                    "信号线周期不应大于慢线周期 " + slow));
        }
        BigDecimal rsiUpper = decimal(merged.get(indicator + "." + IndicatorParamKey.RSI_UPPER));
        BigDecimal rsiLower = decimal(merged.get(indicator + "." + IndicatorParamKey.RSI_LOWER));
        if (rsiUpper != null && rsiLower != null && rsiLower.compareTo(rsiUpper) >= 0) {
            errors.add(SettingFieldError.of(indicator, IndicatorParamKey.RSI_LOWER, "RSI 超卖阈值",
                    "超卖阈值必须低于超买阈值 " + plain(rsiUpper)));
        }

        String alert = ParamGroup.ALERT.name();
        Integer staleDays = integer(merged.get(alert + "." + SettingCatalog.ALERT_DIVIDEND_STALE_DAYS));
        if (staleDays != null && staleDays > 365) {
            errors.add(SettingFieldError.of(alert, AlertParamKey.DIVIDEND_STALE_DAYS, "股息率过期天数",
                    "过期天数不应超过 365 天，否则过期数据会长期参与建议"));
        }
    }

    /**
     * 构造带字段中文名的错误
     *
     * @param def     字段定义
     * @param message 错误原因
     * @return 字段错误
     */
    private SettingFieldError error(SettingFieldCatalog.FieldDef def, String message) {
        return SettingFieldError.of(def.getGroup().name(), def.getKey(), def.getLabel(), message);
    }

    /**
     * 解析分组名，未知返回 null
     *
     * @param name 分组名
     * @return 分组
     */
    private ParamGroup parseGroup(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return ParamGroup.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 解析十进制，空与非法都返回 null
     *
     * @param raw 原始值
     * @return 数值
     */
    private BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析整数，空与非法都返回 null
     *
     * @param raw 原始值
     * @return 整数
     */
    private Integer integer(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
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
        BigDecimal trimmed = value.stripTrailingZeros();
        return trimmed.scale() < 0 ? trimmed.setScale(0, java.math.RoundingMode.UNNECESSARY).toPlainString()
                : trimmed.toPlainString();
    }
}
