package com.lilac.service;

import com.lilac.domain.dto.source.SourceEventQueryRequest;
import com.lilac.domain.vo.SourceHealthVO;

/**
 * 数据源探活业务接口，汇总各源健康度并给出降级矩阵
 */
public interface SourceHealthService {

    /**
     * 取各数据源的健康度与降级矩阵
     *
     * @param request 查询请求，日期为空时默认最近 7 天
     * @return 健康总览
     */
    SourceHealthVO health(SourceEventQueryRequest request);

    /**
     * 清掉过期的取数留痕，返回删除行数
     *
     * @return 删除行数
     */
    int purge();
}
