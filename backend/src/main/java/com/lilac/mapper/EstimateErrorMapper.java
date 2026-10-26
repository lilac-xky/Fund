package com.lilac.mapper;

import com.lilac.domain.entity.EstimateError;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 误差历史数据访问
 */
@Mapper
public interface EstimateErrorMapper extends BaseMapper<EstimateError> {
}
