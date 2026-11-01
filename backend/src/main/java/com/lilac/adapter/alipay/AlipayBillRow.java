package com.lilac.adapter.alipay;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 支付宝账单明细中的一条原始记录，按官方导出列一一对应，不做语义推断
 */
@Data
public class AlipayBillRow {

    /**
     * 交易号，同一笔订单的多次变更共用一个号
     */
    private String tradeNo;

    /**
     * 交易创建时间；注意这是支付宝侧记录时间，基金申赎的「下单时间」要看交易号前 8 位
     */
    private LocalDateTime createdTime;

    /**
     * 下单日，取自交易号前 8 位；账单的「交易创建时间」在卖出与转换行里是确认时间，不能拿来算净值
     */
    private LocalDate orderDate;

    /**
     * 付款时间，网络交易可能为空
     */
    private LocalDateTime paidTime;

    /**
     * 交易对方
     */
    private String counterparty;

    /**
     * 商品名称，基金的类型与名称都藏在这一列里
     */
    private String productName;

    /**
     * 金额，元
     */
    private BigDecimal amount;

    /**
     * 收/支，支出或收入
     */
    private String direction;

    /**
     * 交易状态，交易成功或交易关闭
     */
    private String status;

    /**
     * 备注
     */
    private String note;

    /**
     * 资金状态，已支出、已收入或资金转移
     */
    private String fundStatus;

    /**
     * 金额（元）的原始文本，解析失败时留空便于定位
     */
    private String rawAmount;

    /**
     * 原始行的列文本，解析出问题时按列号回看
     */
    private List<String> cells = new ArrayList<>();
}
