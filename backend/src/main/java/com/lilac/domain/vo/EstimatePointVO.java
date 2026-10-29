package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预估轨迹上的一个采样点
 */
@Data
public class EstimatePointVO implements Serializable {

    /**
     * 采样时刻
     */
    private LocalDateTime time;

    /**
     * 该时刻的整体预估涨跌幅，百分点；无 A 档基金时为 null
     */
    private BigDecimal change;

    /**
     * 该时刻的可预估只数
     */
    private Integer estimableCount;

    private static final long serialVersionUID = 1L;
}
