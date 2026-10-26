package com.lilac.mapper;

import com.lilac.domain.entity.Holding;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 持仓数据访问
 */
@Mapper
public interface HoldingMapper extends BaseMapper<Holding> {
}
