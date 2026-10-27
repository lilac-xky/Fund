package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基金全集同步结果
 */
@Data
public class FundCatalogSyncVO implements Serializable {

    /**
     * 同步后的总条数
     */
    private Long total;

    /**
     * 同步完成时间
     */
    private LocalDateTime syncTime;

    /**
     * 同步耗时毫秒
     */
    private Long costMs;

    private static final long serialVersionUID = 1L;
}
