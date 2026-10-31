package com.lilac.service;

import com.lilac.domain.dto.report.OperationLogQueryRequest;
import com.lilac.domain.vo.OperationLogVO;

import java.util.List;

/**
 * 操作日志业务接口，把提醒发送与买卖变动合并成统一时间线并导出 CSV
 */
public interface OperationLogService {

    /**
     * 合并提醒发送与买卖变动为统一时间线，按发生时间倒序
     *
     * @param request 查询请求
     * @return 操作日志
     */
    List<OperationLogVO> list(OperationLogQueryRequest request);

    /**
     * 导出操作日志 CSV，Excel 打开中文不乱码
     *
     * @param request 查询请求
     * @return 导出说明，含行数与文件路径
     */
    String exportCsv(OperationLogQueryRequest request);
}
