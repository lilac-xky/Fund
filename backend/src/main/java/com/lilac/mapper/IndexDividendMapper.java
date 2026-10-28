package com.lilac.mapper;

import com.lilac.domain.entity.IndexDividend;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 指数股息率数据访问
 */
@Mapper
public interface IndexDividendMapper extends BaseMapper<IndexDividend> {
}
