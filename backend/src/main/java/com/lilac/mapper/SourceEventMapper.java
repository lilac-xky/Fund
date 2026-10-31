package com.lilac.mapper;

import com.lilac.domain.entity.SourceEventRecord;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源取数留痕数据访问
 */
@Mapper
public interface SourceEventMapper extends BaseMapper<SourceEventRecord> {
}
