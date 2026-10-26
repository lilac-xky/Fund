package com.lilac.service;

import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.vo.HoldingVO;

import java.util.List;

/**
 * 持仓业务接口
 */
public interface HoldingService {

    /**
     * 统一查询未删除的持仓
     *
     * @param request 查询请求
     * @return 持仓视图列表
     */
    List<HoldingVO> listHolding(HoldingQueryRequest request);
}
