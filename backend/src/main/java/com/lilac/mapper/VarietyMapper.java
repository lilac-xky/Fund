package com.lilac.mapper;

import com.lilac.domain.entity.Variety;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 品种注册表数据访问
 */
@Mapper
public interface VarietyMapper extends BaseMapper<Variety> {
}
