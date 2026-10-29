package com.lilac.engine.model;

import com.lilac.domain.vo.EstimatePointVO;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 今日预估的盘中采样轨迹，每个点是一次预估的整体涨跌幅
 */
@Data
public class EstimateTrack {

    /**
     * 行情所属交易日
     */
    private LocalDate tradeDate;

    /**
     * 采样点，按时间升序
     */
    private List<EstimatePointVO> points = new ArrayList<>();
}
