package com.lilac.mapper;

import com.lilac.domain.entity.FundNetWorth;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 基金历史净值数据访问
 */
@Mapper
public interface FundNetWorthMapper extends BaseMapper<FundNetWorth> {
}
