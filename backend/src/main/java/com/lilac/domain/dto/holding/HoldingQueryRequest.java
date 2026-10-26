package com.lilac.domain.dto.holding;

import com.lilac.enums.HoldingStatus;
import lombok.Data;

import java.io.Serializable;

/**
 * 持仓查询请求，各字段为空时不参与过滤
 */
@Data
public class HoldingQueryRequest implements Serializable {

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 持仓状态
     */
    private HoldingStatus status;

    private static final long serialVersionUID = 1L;
}
