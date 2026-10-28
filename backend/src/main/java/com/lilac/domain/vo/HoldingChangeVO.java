package com.lilac.domain.vo;

import com.lilac.enums.HoldingChangeType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 持仓变动历史视图
 */
@Data
public class HoldingChangeVO implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * 所持品种的业务主键
     */
    private String varietyKey;

    /**
     * 品种名称，关联品种注册表带出，便于逐笔核对
     */
    private String varietyName;

    /**
     * 变动类型
     */
    private HoldingChangeType changeType;

    /**
     * 变动份额，正数
     */
    private BigDecimal shares;

    /**
     * 成交单价
     */
    private BigDecimal unitPrice;

    /**
     * 变动后合计份额
     */
    private BigDecimal afterShares;

    /**
     * 备注
     */
    private String note;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
