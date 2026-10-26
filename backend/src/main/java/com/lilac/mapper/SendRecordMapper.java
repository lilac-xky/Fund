package com.lilac.mapper;

import com.lilac.domain.entity.SendRecord;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发送留痕数据访问
 */
@Mapper
public interface SendRecordMapper extends BaseMapper<SendRecord> {
}
