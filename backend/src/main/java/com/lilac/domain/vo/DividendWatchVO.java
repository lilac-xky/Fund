package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 红利监控对象，一只持有的红利基金一行，把它的跟踪指数、股息率与自己的参数合在一起
 */
@Data
public class DividendWatchVO implements Serializable {

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 6 位代码
     */
    private String varietyCode;

    /**
     * 标的名称
     */
    private String varietyName;

    /**
     * 跟踪指数代码，未登记时为 null
     */
    private String indexCode;

    /**
     * 跟踪指数全称
     */
    private String indexName;

    /**
     * 当前持有金额，单位元；取不到时为 null
     */
    private BigDecimal currentAmount;

    /**
     * 当前持有金额的取数说明
     */
    private String amountSource;

    /**
     * 当前股息率，单位百分点；取不到时为 null
     */
    private BigDecimal dividendYield;

    /**
     * 股息率数据日期
     */
    private LocalDate dataDate;

    /**
     * 股息率距今天数
     */
    private Integer ageDays;

    /**
     * 股息率是否已过期，过期时不给加减仓动作
     */
    private Boolean stale;

    /**
     * 这只基金自己的基准仓位金额，单位元
     */
    private BigDecimal baseAmount;

    /**
     * 这只基金自己的保底分红金额，单位元
     */
    private BigDecimal floorAmount;

    /**
     * 这只基金自己的加仓股息率阈值，单位百分点
     */
    private BigDecimal addYield;

    /**
     * 这只基金自己的减仓股息率阈值，单位百分点
     */
    private BigDecimal reduceYield;

    /**
     * 这只基金的参数是否齐全且自洽
     */
    private Boolean paramUsable;

    /**
     * 参数问题清单，可用时为空
     */
    private List<String> paramIssues;

    /**
     * 参数提示，如「正在沿用旧版共用参数」
     */
    private List<String> paramNotes;

    /**
     * 按当前参数与本日股息率算出的动作中文名，供列表直接显示
     */
    private String actionLabel;

    /**
     * 按当前参数与本日股息率算出的建议金额，单位元
     */
    private BigDecimal suggestAmount;

    /**
     * 价格穿越网格时的提示，供列表说明红利与网格的优先级
     */
    private String gridNote;

    /**
     * 该标的是否能正常参与建议，false 时看 reason
     */
    private Boolean usable;

    /**
     * 不能参与建议的原因，逐条可读
     */
    private List<String> reasons;

    private static final long serialVersionUID = 1L;
}
