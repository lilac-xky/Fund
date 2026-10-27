package com.lilac.service;

import com.lilac.adapter.model.FundNetWorthPoint;

import java.time.LocalDate;
import java.util.List;

/**
 * 基金历史净值业务接口，负责把上游净值落库并按区间查询
 */
public interface FundNetWorthService {

    /**
     * 增量写入一只基金的新净值，库内已有的日期跳过
     *
     * @param fundCode 6 位基金代码
     * @param points   净值序列
     * @return 实际新增行数
     */
    int saveIncremental(String fundCode, List<FundNetWorthPoint> points);

    /**
     * 按区间查询净值序列，日期升序
     *
     * @param fundCode 6 位基金代码
     * @param start    起始日期，可为 null 表示不限
     * @param end      结束日期，可为 null 表示不限
     * @return 净值序列
     */
    List<FundNetWorthPoint> listRange(String fundCode, LocalDate start, LocalDate end);

    /**
     * 查询某只基金在库内的最新净值日期
     *
     * @param fundCode 6 位基金代码
     * @return 最新净值日期，无数据返回 null
     */
    LocalDate latestDate(String fundCode);
}
