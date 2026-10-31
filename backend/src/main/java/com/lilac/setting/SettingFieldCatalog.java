package com.lilac.setting;

import com.lilac.constant.AlertParamKey;
import com.lilac.constant.BondParamKey;
import com.lilac.constant.DividendParamKey;
import com.lilac.constant.GridParamKey;
import com.lilac.constant.IndicatorParamKey;
import com.lilac.constant.SettingCatalog;
import com.lilac.enums.ParamGroup;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数字段定义表，集中维护每个字段的中文名、单位、控件类型与默认值
 */
public final class SettingFieldCatalog {

    /**
     * 全部字段定义，按分组与页面顺序排列
     *
     * @return 字段定义
     */
    public static List<FieldDef> all() {
        List<FieldDef> defs = new ArrayList<>();
        defs.add(new FieldDef(ParamGroup.DIVIDEND, DividendParamKey.BASE_AMOUNT, "基准仓位金额",
                DividendParamKey.BASE_AMOUNT_DESC, "元", SettingCatalog.TYPE_DECIMAL, null));
        defs.add(new FieldDef(ParamGroup.DIVIDEND, DividendParamKey.FLOOR_AMOUNT, "保底分红金额",
                DividendParamKey.FLOOR_AMOUNT_DESC, "元", SettingCatalog.TYPE_DECIMAL, null));
        defs.add(new FieldDef(ParamGroup.DIVIDEND, DividendParamKey.ADD_YIELD, "加仓股息率阈值",
                DividendParamKey.ADD_YIELD_DESC, "%", SettingCatalog.TYPE_DECIMAL,
                DividendParamKey.DEFAULT_ADD_YIELD.toPlainString()));
        defs.add(new FieldDef(ParamGroup.DIVIDEND, DividendParamKey.REDUCE_YIELD, "减仓股息率阈值",
                DividendParamKey.REDUCE_YIELD_DESC, "%", SettingCatalog.TYPE_DECIMAL,
                DividendParamKey.DEFAULT_REDUCE_YIELD.toPlainString()));

        defs.add(new FieldDef(ParamGroup.GRID, GridParamKey.LOWER_BOUND, "价格下界",
                GridParamKey.LOWER_BOUND_DESC, "元", SettingCatalog.TYPE_DECIMAL, null));
        defs.add(new FieldDef(ParamGroup.GRID, GridParamKey.UPPER_BOUND, "价格上界",
                GridParamKey.UPPER_BOUND_DESC, "元", SettingCatalog.TYPE_DECIMAL, null));
        defs.add(new FieldDef(ParamGroup.GRID, GridParamKey.STEP, "格子大小",
                GridParamKey.STEP_DESC, "元", SettingCatalog.TYPE_DECIMAL, null));
        defs.add(new FieldDef(ParamGroup.GRID, GridParamKey.AMOUNT_PER_GRID, "每格金额",
                GridParamKey.AMOUNT_PER_GRID_DESC, "元", SettingCatalog.TYPE_DECIMAL, null));

        defs.add(new FieldDef(ParamGroup.BOND, BondParamKey.WINDOW, "回看窗口",
                BondParamKey.WINDOW_DESC, "日", SettingCatalog.TYPE_INTEGER, BondParamKey.DEFAULT_WINDOW));
        defs.add(new FieldDef(ParamGroup.BOND, BondParamKey.SIDEWAYS_BAND, "横盘带宽",
                BondParamKey.SIDEWAYS_BAND_DESC, "%", SettingCatalog.TYPE_DECIMAL,
                BondParamKey.DEFAULT_SIDEWAYS_BAND));
        defs.add(new FieldDef(ParamGroup.BOND, BondParamKey.SIDEWAYS_DAYS, "横盘天数阈值",
                BondParamKey.SIDEWAYS_DAYS_DESC, "日", SettingCatalog.TYPE_INTEGER,
                BondParamKey.DEFAULT_SIDEWAYS_DAYS));
        defs.add(new FieldDef(ParamGroup.BOND, BondParamKey.SIDEWAYS_RETURN, "横盘涨幅上限",
                BondParamKey.SIDEWAYS_RETURN_DESC, "%", SettingCatalog.TYPE_DECIMAL,
                BondParamKey.DEFAULT_SIDEWAYS_RETURN));
        defs.add(new FieldDef(ParamGroup.BOND, BondParamKey.WEAK_SLOPE, "转弱斜率阈值",
                BondParamKey.WEAK_SLOPE_DESC, "%", SettingCatalog.TYPE_DECIMAL,
                BondParamKey.DEFAULT_WEAK_SLOPE));
        defs.add(new FieldDef(ParamGroup.BOND, BondParamKey.WEAK_VOLATILITY, "转弱波动率下限",
                BondParamKey.WEAK_VOLATILITY_DESC, "%", SettingCatalog.TYPE_DECIMAL,
                BondParamKey.DEFAULT_WEAK_VOLATILITY));

        defs.add(new FieldDef(ParamGroup.INDICATOR, IndicatorParamKey.RSI_PERIOD, "RSI 周期",
                "RSI 计算所用交易日数", "日", SettingCatalog.TYPE_INTEGER, IndicatorParamKey.DEFAULT_RSI_PERIOD));
        defs.add(new FieldDef(ParamGroup.INDICATOR, IndicatorParamKey.RSI_UPPER, "RSI 超买阈值",
                "RSI 高于该值判为超买", "", SettingCatalog.TYPE_DECIMAL, IndicatorParamKey.DEFAULT_RSI_UPPER));
        defs.add(new FieldDef(ParamGroup.INDICATOR, IndicatorParamKey.RSI_LOWER, "RSI 超卖阈值",
                "RSI 低于该值判为超卖", "", SettingCatalog.TYPE_DECIMAL, IndicatorParamKey.DEFAULT_RSI_LOWER));
        defs.add(new FieldDef(ParamGroup.INDICATOR, IndicatorParamKey.KDJ_PERIOD, "KDJ 的 RSV 周期",
                "计算 RSV 所用交易日数", "日", SettingCatalog.TYPE_INTEGER, IndicatorParamKey.DEFAULT_KDJ_PERIOD));
        defs.add(new FieldDef(ParamGroup.INDICATOR, IndicatorParamKey.KDJ_K_SMOOTH, "KDJ 的 K 平滑",
                "K 值平滑系数", "", SettingCatalog.TYPE_INTEGER, IndicatorParamKey.DEFAULT_KDJ_K_SMOOTH));
        defs.add(new FieldDef(ParamGroup.INDICATOR, IndicatorParamKey.KDJ_D_SMOOTH, "KDJ 的 D 平滑",
                "D 值平滑系数", "", SettingCatalog.TYPE_INTEGER, IndicatorParamKey.DEFAULT_KDJ_D_SMOOTH));
        defs.add(new FieldDef(ParamGroup.INDICATOR, IndicatorParamKey.MACD_FAST, "MACD 快线周期",
                "快线 EMA 周期", "日", SettingCatalog.TYPE_INTEGER, IndicatorParamKey.DEFAULT_MACD_FAST));
        defs.add(new FieldDef(ParamGroup.INDICATOR, IndicatorParamKey.MACD_SLOW, "MACD 慢线周期",
                "慢线 EMA 周期，须大于快线", "日", SettingCatalog.TYPE_INTEGER, IndicatorParamKey.DEFAULT_MACD_SLOW));
        defs.add(new FieldDef(ParamGroup.INDICATOR, IndicatorParamKey.MACD_SIGNAL, "MACD 信号线周期",
                "信号线 DEA 周期", "日", SettingCatalog.TYPE_INTEGER, IndicatorParamKey.DEFAULT_MACD_SIGNAL));
        defs.add(new FieldDef(ParamGroup.INDICATOR, IndicatorParamKey.PARTICIPATE_IN_ALERT, "指标参与提醒",
                "关闭后指标只在午盘页展示，不触发交易提醒", "", SettingCatalog.TYPE_BOOLEAN,
                IndicatorParamKey.DEFAULT_PARTICIPATE_IN_ALERT));

        defs.add(new FieldDef(ParamGroup.ALERT, SettingCatalog.ALERT_SUSPEND_STREAK, "连续超阈值期数",
                "单只基金连续多少期误差超阈值后提示停用预估", "期", SettingCatalog.TYPE_INTEGER,
                SettingCatalog.DEFAULT_SUSPEND_STREAK));
        defs.add(new FieldDef(ParamGroup.ALERT, SettingCatalog.ALERT_DIVIDEND_STALE_DAYS, "股息率过期天数",
                "股息率数据超过该天数即标为过期，过期不得参与加减仓建议", "天", SettingCatalog.TYPE_INTEGER,
                SettingCatalog.DEFAULT_DIVIDEND_STALE_DAYS));
        defs.add(new FieldDef(ParamGroup.ALERT, AlertParamKey.BOND_ERROR_THRESHOLD, "债券误差阈值",
                "债券日波动极小，套用 0.30 会让阈值失去意义；默认 0.10，改这里会覆盖配置文件", "%",
                SettingCatalog.TYPE_DECIMAL, SettingCatalog.DEFAULT_BOND_ERROR_THRESHOLD));
        return defs;
    }

    /**
     * 按分组取字段定义
     *
     * @param group 分组
     * @return 该分组的字段定义
     */
    public static List<FieldDef> of(ParamGroup group) {
        List<FieldDef> matched = new ArrayList<>();
        for (FieldDef def : all()) {
            if (def.getGroup() == group) {
                matched.add(def);
            }
        }
        return matched;
    }

    /**
     * 按分组与字段名取定义
     *
     * @param group 分组
     * @param key   字段名
     * @return 字段定义，未登记返回 null
     */
    public static FieldDef find(ParamGroup group, String key) {
        for (FieldDef def : all()) {
            if (def.getGroup() == group && def.getKey().equals(key)) {
                return def;
            }
        }
        return null;
    }

    /**
     * 是否属于可写的分组，邮件与 SMTP 参数不进数据库，故不在其中
     *
     * @param group 分组
     * @return 可写返回 true
     */
    public static boolean writable(ParamGroup group) {
        return group == ParamGroup.DIVIDEND || group == ParamGroup.GRID || group == ParamGroup.BOND
                || group == ParamGroup.INDICATOR || group == ParamGroup.ALERT;
    }

    private SettingFieldCatalog() {
    }

    /**
     * 一个字段的定义，页面按它渲染标签、单位与默认值
     */
    @Data
    public static class FieldDef {

        /**
         * 所属分组
         */
        private final ParamGroup group;

        /**
         * 字段名，与参数键同名
         */
        private final String key;

        /**
         * 字段中文名
         */
        private final String label;

        /**
         * 字段说明
         */
        private final String description;

        /**
         * 单位
         */
        private final String unit;

        /**
         * 控件类型
         */
        private final String type;

        /**
         * 内置默认值，没有则为 null
         */
        private final String defaultValue;
    }
}
