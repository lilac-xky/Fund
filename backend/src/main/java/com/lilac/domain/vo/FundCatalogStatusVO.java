package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基金全集缓存状态
 */
@Data
public class FundCatalogStatusVO implements Serializable {

    /**
     * 缓存总条数
     */
    private Long total;

    /**
     * 最近一次同步时间
     */
    private LocalDateTime syncTime;

    /**
     * 是否已过期，过期时下次消歧会触发同步
     */
    private Boolean stale;

    /**
     * 允许的最大缓存天数
     */
    private Integer maxAgeDays;

    private static final long serialVersionUID = 1L;
}
