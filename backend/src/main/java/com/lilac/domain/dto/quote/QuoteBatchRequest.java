package com.lilac.domain.dto.quote;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量行情请求，用于一次取回多只重仓股或指数的当日涨跌幅
 */
@Data
public class QuoteBatchRequest implements Serializable {

    /**
     * 标的代码列表，可带 sh/sz/bj/hk 前缀，不带时按号段推断
     */
    private List<String> codes;

    /**
     * 日线根数，只在查历史时使用，不传取配置默认值
     */
    private Integer historyLimit;

    private static final long serialVersionUID = 1L;
}
