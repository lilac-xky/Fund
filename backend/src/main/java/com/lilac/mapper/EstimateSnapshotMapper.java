package com.lilac.mapper;

import com.lilac.domain.entity.EstimateSnapshot;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 当日预估留存数据访问
 */
@Mapper
public interface EstimateSnapshotMapper extends BaseMapper<EstimateSnapshot> {
}
