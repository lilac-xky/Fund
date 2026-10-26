package com.lilac.domain.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务实体基类，统一雪花主键与创建、更新时间
 */
@Data
public abstract class BaseEntity implements Serializable {

    /**
     * 主键，应用侧雪花生成；数据库侧另有 AUTO_INCREMENT 兜底
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 创建时间，由数据库默认值填写
     */
    private LocalDateTime createTime;

    /**
     * 更新时间，更新时由数据库自动刷新
     */
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
