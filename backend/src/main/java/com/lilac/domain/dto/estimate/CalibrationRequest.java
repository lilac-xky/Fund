package com.lilac.domain.dto.estimate;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 晚间校准请求，交易日与基金列表均可空，空时按当日与全部持有中的持仓
 */
@Data
public class CalibrationRequest implements Serializable {

    /**
     * 待校准交易日，为空时取当日
     */
    private LocalDate tradeDate;

    /**
     * 指定基金，为空时按全部持有中的持仓
     */
    private java.util.List<String> varietyKeys;

    /**
     * 是否在预估留存缺失时用当日行情重算，默认 false 只报缺口
     */
    private Boolean recompute;

    private static final long serialVersionUID = 1L;
}
