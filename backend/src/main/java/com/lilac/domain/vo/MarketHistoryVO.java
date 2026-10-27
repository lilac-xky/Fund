package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 单只标的的日线视图，供指标计算与回溯取价
 */
@Data
public class MarketHistoryVO implements Serializable {

    /**
     * 带交易所前缀的完整代码
     */
    private String symbol;

    /**
     * 日线，按日期升序
     */
    private List<MarketBarVO> bars;

    /**
     * 根数
     */
    private Integer size;

    /**
     * 最新交易日
     */
    private LocalDate latestDate;

    /**
     * 最新收盘价
     */
    private BigDecimal latestClose;

    /**
     * 是否前复权口径
     */
    private Boolean adjusted;

    /**
     * 取数来源
     */
    private String source;

    private static final long serialVersionUID = 1L;
}
