package com.lilac.domain.vo;

import com.lilac.enums.DividendMethod;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.VarietyType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 持仓总览的单品种聚合行
 */
@Data
public class HoldingVarietyVO implements Serializable {

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 品种类型
     */
    private VarietyType varietyType;

    /**
     * 6 位代码
     */
    private String varietyCode;

    /**
     * 标的名称
     */
    private String varietyName;

    /**
     * 基金类型，UNKNOWN 表示待确认
     */
    private FundCategory category;

    /**
     * 基金类型中文名
     */
    private String categoryLabel;

    /**
     * 类型是否已确认
     */
    private Boolean confirmed;

    /**
     * 合计持有份额，同一品种多条持仓合并
     */
    private BigDecimal totalShares;

    /**
     * 加权平均成本价，按份额加权
     */
    private BigDecimal avgCostPrice;

    /**
     * 最新单位净值，场内 ETF 用最新行情价；取不到为 null
     */
    private BigDecimal latestPrice;

    /**
     * 最新净值或行情的日期
     */
    private LocalDate latestPriceDate;

    /**
     * 最新价的取数说明，如「净值 2026-10-27」或「行情价（实时）」
     */
    private String priceSource;

    /**
     * 最新一条持仓记录的建仓日期
     */
    private LocalDate lastOpenDate;

    /**
     * 持仓状态，全部记录 CLOSED 时为 CLOSED
     */
    private HoldingStatus status;

    /**
     * 持仓成本金额，份额乘加权成本价
     */
    private BigDecimal costAmount;

    /**
     * 持仓市值，份额乘最新价；最新价缺失时为 null
     */
    private BigDecimal marketValue;

    /**
     * 浮盈亏，市值减成本金额；最新价缺失时为 null
     */
    private BigDecimal profitLoss;

    /**
     * 浮盈亏比例，单位百分点；最新价缺失时为 null
     */
    private BigDecimal profitRate;

    /**
     * 含现金分红的总收益，与支付宝「持有收益」同口径；最新价缺失时为 null
     */
    private BigDecimal totalProfitLoss;

    /**
     * 含现金分红的总收益率，单位百分点；最新价缺失时为 null
     */
    private BigDecimal totalProfitRate;

    /**
     * 当日涨跌幅，单位百分点；没有可用日变动时为 null
     */
    private BigDecimal dailyChangePercent;

    /**
     * 当日收益金额，单位元；按当日涨跌幅反推昨日市值后相减，缺日变动时为 null
     */
    private BigDecimal dailyProfitLoss;

    /**
     * 持仓期间累计的现金分红金额，单位元；无除息记录时为 0
     */
    private BigDecimal dividendAmount;

    /**
     * 分红口径说明，如「自 2026-07-31 建仓以来共 2 次除息」
     */
    private String dividendNote;

    /**
     * 分红方式，红利再投时现金分红为 0，分红已折成份额
     */
    private DividendMethod dividendMethod;

    /**
     * 逐笔除息明细，页面据此与平台核对，不参与任何金额计算
     */
    private List<HoldingDividendVO> dividendDetails;

    /**
     * 持有中的批次，供页面更正份额、成本价与建仓日期
     */
    private List<HoldingBatchVO> batches;

    private static final long serialVersionUID = 1L;
}
