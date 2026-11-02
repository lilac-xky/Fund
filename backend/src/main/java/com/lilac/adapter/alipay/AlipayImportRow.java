package com.lilac.adapter.alipay;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入预览里的一行，已完成去重、退款抵消与代码匹配，等人工确认
 */
@Data
public class AlipayImportRow {

    /**
     * 行内序号，前端回传时用它定位
     */
    private Integer seq;

    /**
     * 原始交易号，提交时用于留痕与二次去重
     */
    private String tradeNo;

    /**
     * 识别出的动作
     */
    private String action;

    /**
     * 动作中文名
     */
    private String actionLabel;

    /**
     * 从账单剥出的基金名称
     */
    private String fundName;

    /**
     * 匹配到的 6 位代码，未匹配或歧义时为空
     */
    private String fundCode;

    /**
     * 匹配到的品种业务主键，需先注册才能落库
     */
    private String varietyKey;

    /**
     * 匹配候选，多个时由人工选
     */
    private List<Candidate> candidates = new ArrayList<>();

    /**
     * 转换行的转入基金名称
     */
    private String targetFundName;

    /**
     * 转换转入基金匹配到的候选
     */
    private List<Candidate> targetCandidates = new ArrayList<>();

    /**
     * 金额，元
     */
    private BigDecimal amount;

    /**
     * 转换行退回余额宝的找零金额，元
     */
    private BigDecimal refundAmount;

    /**
     * 转换转出所得合计，等于账单金额加找零，元
     */
    private BigDecimal transferOutAmount;

    /**
     * 下单时间
     */
    private LocalDateTime orderTime;

    /**
     * 按 15 点口径换算的净值确认日
     */
    private LocalDate confirmDate;

    /**
     * 该行状态：可导入 / 待补信息 / 已存在 / 已抵消
     */
    private String status;

    /**
     * 状态说明，直接展示给使用者
     */
    private String reason;

    /**
     * 是否默认为勾选状态
     */
    private Boolean selectable;

    /**
     * 候选基金
     */
    @Data
    public static class Candidate {

        /**
         * 6 位代码
         */
        private String fundCode;

        /**
         * 基金简称
         */
        private String fundName;

        /**
         * 天天基金原始类型
         */
        private String fundType;

        /**
         * 是否已在品种表注册，已注册才能直接落库
         */
        private Boolean registered;

        /**
         * 已注册时的品种业务主键
         */
        private String varietyKey;

        /**
         * 与账单名称的相似度，0 到 1，供人工判断为何匹配到这只
         */
        private Double similarity;
    }
}
