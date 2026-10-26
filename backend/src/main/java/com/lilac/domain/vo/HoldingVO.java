package com.lilac.domain.vo;

import com.lilac.enums.HoldingStatus;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 持仓视图对象
 */
@Data
public class HoldingVO implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * 所持品种的业务主键
     */
    private String varietyKey;

    /**
     * 持有份额
     */
    private BigDecimal shares;

    /**
     * 成本价
     */
    private BigDecimal costPrice;

    /**
     * 建仓日期
     */
    private LocalDate openDate;

    /**
     * 持仓状态
     */
    private HoldingStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
