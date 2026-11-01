package com.lilac.adapter.alipay;

import com.lilac.enums.AlipayAction;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 从账单行识别出的一条基金动作，已剥掉平台前缀并判出类型
 */
@Data
public class AlipayFundOrder {

    /**
     * 原始交易号，去重用
     */
    private String tradeNo;

    /**
     * 原始商品名称，留档核对
     */
    private String productName;

    /**
     * 从商品名称里剥出的基金名称，如「华商均衡成长混合C」
     */
    private String fundName;

    /**
     * 转换的转入基金名称，仅转换行有值
     */
    private String targetFundName;

    /**
     * 动作类型
     */
    private AlipayAction action;

    /**
     * 金额，元
     */
    private BigDecimal amount;

    /**
     * 下单时间，即账单里的交易创建时间
     */
    private LocalDateTime orderTime;

    /**
     * 按 15:00 口径换算出的净值确认日，交易日 15 点后顺延
     */
    private LocalDate confirmDate;

    /**
     * 交易号前 8 位表示的下单日，不做 15 点顺延；转换与它的退款靠这个字段配对
     */
    private LocalDate tradeDate;

    /**
     * 原始行备注
     */
    private String note;

    /**
     * 若该行被判定为退款，其冲销的原交易号
     */
    private String refundedTradeNo;
}
