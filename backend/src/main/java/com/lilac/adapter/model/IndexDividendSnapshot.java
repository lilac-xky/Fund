package com.lilac.adapter.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 一次股息率抓取的结果，带数据日期与近期序列
 */
@Data
public class IndexDividendSnapshot {

    /**
     * 指数代码
     */
    private String indexCode;

    /**
     * 指数中文全称，取自文件的中文名称列
     */
    private String indexName;

    /**
     * 最新一期数据日期，页面与邮件必须显示它
     */
    private LocalDate dataDate;

    /**
     * 最新一期股息率，单位百分点
     */
    private BigDecimal dividendYield;

    /**
     * 近期序列，按日期降序
     */
    private List<IndexDividendPoint> points;
}
