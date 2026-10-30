package com.lilac.domain.dto.holding;

import lombok.Data;

import java.io.Serializable;

/**
 * 持仓批次撤销请求，用于把录错的整笔持仓作废
 */
@Data
public class HoldingChangeRevokeRequest implements Serializable {

    /**
     * 持仓批次主键
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
