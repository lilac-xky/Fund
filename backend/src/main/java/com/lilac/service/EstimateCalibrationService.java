package com.lilac.service;

import com.lilac.domain.dto.estimate.CalibrationRequest;
import com.lilac.domain.vo.CalibrationResultVO;
import com.lilac.domain.vo.EstimateErrorTrendVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 晚间校准业务接口，用当日实际净值反向检验白天给出的预估，并滚动判定基金档位
 */
public interface EstimateCalibrationService {

    /**
     * 执行某一交易日的晚间校准，重复执行同一天只覆盖不重复累计
     *
     * @param request 校准请求
     * @return 校准汇总
     */
    CalibrationResultVO calibrate(CalibrationRequest request);

    /**
     * 查询单只基金的误差趋势，供复盘页与停用判断
     *
     * @param varietyKey 品种业务主键
     * @param days       回溯自然日数
     * @return 误差趋势
     */
    EstimateErrorTrendVO trend(String varietyKey, int days);

    /**
     * 列出需要提示「建议停用该基金预估」的基金
     *
     * @param tradeDate 判定日
     * @return 建议停用清单
     */
    List<EstimateErrorTrendVO> suspensionHints(LocalDate tradeDate);
}
