package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 红利监控对象，说明这套参数管的是哪只红利基金与它跟踪的指数
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
     * 该标的是否能正常参与建议，false 时看 reason
     */
    private Boolean usable;

    /**
     * 不能参与建议的原因，逐条可读
     */
    private List<String> reasons;

    private static final long serialVersionUID = 1L;
}
