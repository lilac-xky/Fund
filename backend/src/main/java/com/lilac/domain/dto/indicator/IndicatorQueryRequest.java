package com.lilac.domain.dto.indicator;

import lombok.Data;

import java.io.Serializable;

/**
 * 技术指标查询请求，按代码或品种主键定位，两者都不填时取全部持仓
 */
@Data
public class IndicatorQueryRequest implements Serializable {

    /**
     * 裸代码或带前缀代码，与 varietyKey 二选一
     */
    private String code;

    /**
     * 品种业务主键，与 code 二选一
     */
    private String varietyKey;

    /**
     * 返回的日线根数，小于等于 0 时按参数需要自动取
     */
    private Integer limit;

    private static final long serialVersionUID = 1L;
}
