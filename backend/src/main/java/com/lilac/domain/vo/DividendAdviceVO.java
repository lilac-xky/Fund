package com.lilac.domain.vo;

import com.lilac.enums.DividendAction;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 单只红利品种的加减仓建议，金额已取整到元并带数据日期
 */
@Data
public class DividendAdviceVO implements Serializable {

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
     * 跟踪指数代码
     */
    private String indexCode;

    /**
     * 动作
     */
    private DividendAction action;

    /**
     * 动作中文名，供页面与邮件回显
     */
    private String actionLabel;

    /**
     * 建议买卖金额，单位元，精确到元
     */
    private BigDecimal suggestAmount;

    /**
     * 目标持仓金额，单位元
     */
    private BigDecimal targetAmount;

    /**
     * 当前持有金额，单位元
     */
    private BigDecimal currentAmount;

    /**
     * 减仓后的剩余金额，单位元
     */
    private BigDecimal remainingAmount;

    /**
     * 基准仓位金额，单位元
     */
    private BigDecimal baseAmount;

    /**
     * 保底分红金额，单位元
     */
    private BigDecimal floorAmount;

    /**
     * 使用的股息率，单位百分点
     */
    private BigDecimal dividendYield;

    /**
     * 股息率数据日期，页面与邮件必须显示
     */
    private LocalDate dataDate;

    /**
     * 距离今天已过天数
     */
    private Long ageDays;

    /**
     * 股息率是否已过期
     */
    private Boolean stale;

    /**
     * 是否因保底分红金额放弃了减仓
     */
    private Boolean floorProtected;

    /**
     * 是否压制了同标的的网格提醒
     */
    private Boolean gridSuppressed;

    /**
     * 网格互斥说明，同一标的不出现冲突建议的口径
     */
    private String gridNote;

    /**
     * 判断依据文字
     */
    private List<String> remarks;

    /**
     * 当前金额的取数说明，手工指定与按净值推算不同
     */
    private String amountSource;

    private static final long serialVersionUID = 1L;
}
