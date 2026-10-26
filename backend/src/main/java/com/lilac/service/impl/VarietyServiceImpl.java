package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.entity.Variety;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.mapper.VarietyMapper;
import com.lilac.service.VarietyService;
import com.lilac.utils.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 品种注册表业务实现
 */
@Slf4j
@Service
public class VarietyServiceImpl implements VarietyService {

    @Resource
    private VarietyMapper varietyMapper;

    /**
     * 统一查询品种
     *
     * @param request 查询请求
     * @return 品种视图列表
     */
    @Override
    public List<VarietyVO> listVariety(VarietyQueryRequest request) {
        VarietyQueryRequest query = request == null ? new VarietyQueryRequest() : request;
        List<Variety> rows = varietyMapper.selectListByQuery(QueryWrapper.create()
                .eq(Variety::getVarietyKey, query.getVarietyKey(), query.getVarietyKey() != null)
                .eq(Variety::getVarietyCode, query.getVarietyCode(), query.getVarietyCode() != null)
                .eq(Variety::getVarietyType, query.getVarietyType(), query.getVarietyType() != null)
                .eq(Variety::getCategory, query.getCategory(), query.getCategory() != null)
                .eq(Variety::getConfirmed, query.getConfirmed(), query.getConfirmed() != null)
                .orderBy(Variety::getVarietyType, true));
        return BeanUtil.copyToList(rows, VarietyVO.class);
    }

    /**
     * 按业务主键存在则更新、不存在则新增，避免重复录入同一标的撞唯一索引
     *
     * @param request 保存请求
     * @return 是否成功
     */
    @Override
    public boolean saveVariety(VarietySaveRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getVarietyKey() == null || request.getVarietyKey().isBlank(),
                HttpsCodeEnum.PARAMS_ERROR, "品种业务主键不能为空");
        Variety variety = BeanUtil.copyProperties(request, Variety.class);
        VarietyQueryRequest query = new VarietyQueryRequest();
        query.setVarietyKey(request.getVarietyKey());
        List<Variety> existing = varietyMapper.selectListByQuery(QueryWrapper.create()
                .eq(Variety::getVarietyKey, query.getVarietyKey()));
        if (existing.isEmpty()) {
            return varietyMapper.insertSelective(variety) > 0;
        }
        // 沿用已存在行的雪花主键，否则会写成新行并撞上业务唯一索引
        variety.setId(existing.get(0).getId());
        return varietyMapper.update(variety) > 0;
    }
}
