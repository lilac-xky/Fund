package com.lilac.domain.vo;

import com.lilac.enums.GridBasis;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 网格监控对象，一只持仓标的加它自己的格子，带上当前价以便直接判断格子在不在合理区间
 */
@Data
public class GridWatchVO implements Serializable {

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
     * 基金类型中文名
     */
    private String categoryLabel;

    /**
     * 品种类型，OF 场外 / ETF 场内
     */
    private String varietyType;

    /**
     * 判断所用的价格口径，场内实时价或场外尾盘预估净值
     */
    private GridBasis basis;

    /**
     * 价格口径中文名
     */
    private String basisLabel;

    /**
     * 场外基金用来估净值的跟踪 ETF 或指数行情代码
     */
    private String proxySymbol;

    /**
     * 当前价，单位元；取不到时为 null
     */
    private BigDecimal currentPrice;

    /**
     * 当前价的数据日期
     */
    private LocalDate priceDate;

    /**
     * 当前价的来源说明
     */
    private String priceSource;

    /**
     * 该标的的价格下界，单位元
     */
    private BigDecimal lowerBound;

    /**
     * 该标的的价格上界，单位元
     */
    private BigDecimal upperBound;

    /**
     * 该标的的格子大小，单位元
     */
    private BigDecimal step;

    /**
     * 该标的的每格金额，单位元
     */
    private BigDecimal amountPerGrid;

    /**
     * 该标的的参数是否齐全且自洽
     */
    private Boolean paramUsable;

    /**
     * 参数问题清单，可用时为空
     */
    private List<String> paramIssues;

    /**
     * 参数提示，如「当前价已高于上界」
     */
    private List<String> paramNotes;

    /**
     * 该标的能划出的网格线条数
     */
    private Integer lineCount;

    /**
     * 是否已单独配置；false 表示还没配过，网格对它不生效
     */
    private Boolean customized;

    /**
     * 当前价相对格子区间的状态 INSIDE / ABOVE / BELOW，未配置时为 null
     */
    private String position;

    /**
     * 区间状态说明，如「当前价已高于网格上界，需重新划格」
     */
    private String positionNote;

    private static final long serialVersionUID = 1L;
}
