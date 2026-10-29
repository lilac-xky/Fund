package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 今日预估的盘中轨迹，供行情条画出当天走向
 */
@Data
public class EstimateTrackVO implements Serializable {

    /**
     * 行情所属交易日
     */
    private LocalDate tradeDate;

    /**
     * 采样点，按时间升序
     */
    private List<EstimatePointVO> points;

    /**
     * 采样点数，少于两点画不出线
     */
    private Integer pointCount;

    /**
     * 轨迹说明，如「今日首次预估后才开始记录」
     */
    private String note;

    private static final long serialVersionUID = 1L;
}
