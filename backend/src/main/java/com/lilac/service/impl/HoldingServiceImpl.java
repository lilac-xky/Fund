package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.entity.Holding;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.mapper.HoldingMapper;
import com.lilac.service.HoldingService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 持仓业务实现
 */
@Slf4j
@Service
public class HoldingServiceImpl implements HoldingService {

    @Resource
    private HoldingMapper holdingMapper;

    /**
     * 统一查询未删除的持仓
     *
     * @param request 查询请求
     * @return 持仓视图列表
     */
    @Override
    public List<HoldingVO> listHolding(HoldingQueryRequest request) {
        HoldingQueryRequest query = request == null ? new HoldingQueryRequest() : request;
        List<Holding> rows = holdingMapper.selectListByQuery(QueryWrapper.create()
                .eq(Holding::getVarietyKey, query.getVarietyKey(), query.getVarietyKey() != null)
                .eq(Holding::getStatus, query.getStatus(), query.getStatus() != null));
        return BeanUtil.copyToList(rows, HoldingVO.class);
    }
}
