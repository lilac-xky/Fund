package com.lilac.mapper;

import com.lilac.domain.entity.HoldingChange;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 持仓变动历史数据访问
 */
@Mapper
public interface HoldingChangeMapper extends BaseMapper<HoldingChange> {
}
