package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 数据源健康总览，逐源给出可用性与降级影响
 */
@Data
public class SourceHealthVO implements Serializable {

    /**
     * 各数据源的统计，按失败次数降序
     */
    private List<SourceStatVO> sources;

    /**
     * 降级矩阵，说明每个源失效时哪些功能降级
     */
    private List<DegradationRowVO> matrix;

    /**
     * 当前是否存在明显异常的数据源
     */
    private Boolean hasTrouble;

    /**
     * 总结说明
     */
    private String note;

    /**
     * 统计区间起点
     */
    private LocalDate startDate;

    /**
     * 统计区间终点
     */
    private LocalDate endDate;

    private static final long serialVersionUID = 1L;
}
