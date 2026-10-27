package com.lilac.mapper;

import com.lilac.domain.entity.FundCatalog;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 基金全集缓存数据访问
 */
@Mapper
public interface FundCatalogMapper extends BaseMapper<FundCatalog> {
}
