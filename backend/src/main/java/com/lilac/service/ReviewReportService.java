package com.lilac.service;

import com.lilac.domain.vo.ReviewReportVO;

import java.time.LocalDate;

/**
 * 复盘报告业务接口，负责生成当日报告、留存文件与导出 CSV
 */
public interface ReviewReportService {

    /**
     * 生成指定交易日的复盘报告并留存在 data/report 下
     *
     * @param tradeDate 交易日，为空取今日
     * @return 复盘报告
     */
    ReviewReportVO generate(LocalDate tradeDate);

    /**
     * 导出预估与实际对照的 CSV
     *
     * @param tradeDate 交易日，为空取今日
     * @return 报告，savedPath 为 CSV 路径
     */
    ReviewReportVO exportComparison(LocalDate tradeDate);

    /**
     * 导出误差历史区间 CSV
     *
     * @param startDate 起始日，含
     * @param endDate   截止日，含
     * @return 导出的行数与文件路径说明
     */
    String exportErrorHistory(LocalDate startDate, LocalDate endDate);
}
