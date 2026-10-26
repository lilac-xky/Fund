package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.domain.dto.param.ParamQueryRequest;
import com.lilac.domain.dto.param.ParamSaveRequest;
import com.lilac.domain.entity.StrategyParam;
import com.lilac.domain.vo.StrategyParamVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.mapper.StrategyParamMapper;
import com.lilac.service.StrategyParamService;
import com.lilac.utils.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 策略参数业务实现
 */
@Slf4j
@Service
public class StrategyParamServiceImpl implements StrategyParamService {

    @Resource
    private StrategyParamMapper strategyParamMapper;

    /**
     * 统一查询策略参数
     *
     * @param request 查询请求
     * @return 参数视图列表
     */
    @Override
    public List<StrategyParamVO> listParam(ParamQueryRequest request) {
        ParamQueryRequest query = request == null ? new ParamQueryRequest() : request;
        List<StrategyParam> rows = strategyParamMapper.selectListByQuery(QueryWrapper.create()
                .eq(StrategyParam::getParamGroup, query.getParamGroup(), query.getParamGroup() != null)
                .eq(StrategyParam::getParamKey, query.getParamKey(), query.getParamKey() != null)
                .orderBy(StrategyParam::getParamKey, true));
        return BeanUtil.copyToList(rows, StrategyParamVO.class);
    }

    /**
     * 按「分组 + 参数名」存在则更新、不存在则新增，避免设置页重复保存产生脏数据
     *
     * @param request 保存请求
     * @return 是否成功
     */
    @Override
    public boolean saveParam(ParamSaveRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getParamGroup() == null || request.getParamKey() == null,
                HttpsCodeEnum.PARAMS_ERROR, "参数分组与参数名不能为空");
        StrategyParam param = BeanUtil.copyProperties(request, StrategyParam.class);
        List<StrategyParam> existing = strategyParamMapper.selectListByQuery(QueryWrapper.create()
                .eq(StrategyParam::getParamGroup, request.getParamGroup())
                .eq(StrategyParam::getParamKey, request.getParamKey()));
        if (existing.isEmpty()) {
            return strategyParamMapper.insertSelective(param) > 0;
        }
        // 沿用已存在行的主键，否则会写成新行并撞上分组内唯一索引
        param.setId(existing.get(0).getId());
        return strategyParamMapper.update(param) > 0;
    }
}
