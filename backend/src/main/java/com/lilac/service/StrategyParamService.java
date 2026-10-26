package com.lilac.service;

import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.dto.param.ParamSaveRequest;
import com.lilac.domain.vo.StrategyParamVO;

import java.util.List;

/**
 * 策略参数业务接口
 */
public interface StrategyParamService {

    /**
     * 统一查询策略参数
     *
     * @param request 查询请求
     * @return 参数视图列表
     */
    List<StrategyParamVO> listParam(ParamQueryRequest request);

    /**
     * 按「分组 + 参数名」存在则更新、不存在则新增
     *
     * @param request 保存请求
     * @return 是否成功
     */
    boolean saveParam(ParamSaveRequest request);
}
