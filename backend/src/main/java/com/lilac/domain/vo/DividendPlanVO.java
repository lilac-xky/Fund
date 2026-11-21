package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 红利策略参数视图，按标的给出，附问题清单供设置页提示不合法组合
 */
@Data
public class DividendPlanVO implements Serializable {

    /**
     * 所属品种业务主键；旧版共用参数为 null
     */
    private String varietyKey;

    /**
     * 所属品种名称，供页面显示这套参数管的是哪只
     */
    private String varietyName;

    /**
     * 跟踪指数代码，页面据此说明股息率来自哪只指数
     */
    private String indexCode;

    /**
     * 跟踪指数全称
     */
    private String indexName;

    /**
     * 基准仓位金额，单位元
     */
    private BigDecimal baseAmount;

    /**
     * 保底分红金额，单位元
     */
    private BigDecimal floorAmount;

    /**
     * 加仓股息率阈值，单位百分点
     */
    private BigDecimal addYield;

    /**
     * 减仓股息率阈值，单位百分点
     */
    private BigDecimal reduceYield;

    /**
     * 参数是否齐全且自洽
     */
    private Boolean usable;

    /**
     * 参数问题清单，可用时为空
     */
    private List<String> issues;

    /**
     * 参数提示，如「正在沿用旧版共用参数」；不影响可用性
     */
    private List<String> notes;

    private static final long serialVersionUID = 1L;
}
