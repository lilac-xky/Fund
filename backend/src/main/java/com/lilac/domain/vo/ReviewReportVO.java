package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 当日复盘报告，逐只对照预估与实际，并给出误差区间统计
 */
@Data
public class ReviewReportVO implements Serializable {

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 报告生成时间
     */
    private String generatedAt;

    /**
     * 逐只对照明细
     */
    private List<ReportRowVO> rows;

    /**
     * 预估只数
     */
    private Integer estimatedCount;

    /**
     * 已对照只数
     */
    private Integer calibratedCount;

    /**
     * 超阈值只数
     */
    private Integer overThresholdCount;

    /**
     * 误差绝对值中位数，百分点
     */
    private BigDecimal medianAbsError;

    /**
     * 误差绝对值 P90，百分点
     */
    private BigDecimal p90AbsError;

    /**
     * 最大单只误差绝对值，百分点
     */
    private BigDecimal maxAbsError;

    /**
     * 方向命中率，百分点
     */
    private BigDecimal directionAccuracy;

    /**
     * 报告正文里的说明文字
     */
    private List<String> notes;

    /**
     * 报告留存路径，写入失败时为 null
     */
    private String savedPath;

    private static final long serialVersionUID = 1L;
}
