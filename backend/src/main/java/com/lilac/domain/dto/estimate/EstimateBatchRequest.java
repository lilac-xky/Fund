package com.lilac.domain.dto.estimate;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 预估请求，不传品种主键时取全部持有中的持仓
 */
@Data
public class EstimateBatchRequest implements Serializable {

    /**
     * 品种业务主键列表，为空时按全部 HOLDING 状态的持仓预估
     */
    private List<String> varietyKeys;

    /**
     * 是否跳过缓存强制重算
     */
    private Boolean refresh;

    private static final long serialVersionUID = 1L;
}
