package com.lilac.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 净值序列中的一点
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NetWorthPointVO implements Serializable {

    /**
     * 净值日期
     */
    private LocalDate netWorthDate;

    /**
     * 单位净值
     */
    private BigDecimal unitNetWorth;

    /**
     * 当日净值回报，单位百分点
     */
    private BigDecimal equityReturn;

    private static final long serialVersionUID = 1L;
}
