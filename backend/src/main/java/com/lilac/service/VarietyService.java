package com.lilac.service;

import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.vo.VarietyVO;

import java.util.List;

/**
 * 品种注册表业务接口
 */
public interface VarietyService {

    /**
     * 统一查询品种
     *
     * @param request 查询请求
     * @return 品种视图列表
     */
    List<VarietyVO> listVariety(VarietyQueryRequest request);

    /**
     * 按业务主键存在则更新、不存在则新增
     *
     * @param request 保存请求
     * @return 是否成功
     */
    boolean saveVariety(VarietySaveRequest request);
}
