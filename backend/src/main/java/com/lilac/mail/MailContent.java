package com.lilac.mail;

import com.lilac.domain.vo.DividendAdvisoryVO;
import com.lilac.domain.vo.GridAdvisoryVO;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 邮件内容模型，HTML 与纯文本两版都从它渲染，页面也能直接取用
 */
@Data
public class MailContent implements Serializable {

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 纳入邮件的持仓品种数，为 0 时改发「未配置持仓」提示
     */
    private int varietyCount;

    /**
     * 可预估的基金，按预估涨跌幅降序
     */
    private List<Item> items = new ArrayList<>();

    /**
     * 无法预估的基金，单独成段并带原因
     */
    private List<Item> unavailableItems = new ArrayList<>();

    /**
     * 整体预估涨跌，百分点
     */
    private BigDecimal overallChange;

    /**
     * 整体口径说明
     */
    private String overallNote;

    /**
     * 是否处于交易时段
     */
    private Boolean tradingHours;

    /**
     * 预估整体缺失的原因，非缺失为空
     */
    private String estimateUnavailableReason;

    /**
     * 红利建议汇总，无红利持仓为空
     */
    private DividendAdvisoryVO dividendAdvisory;

    /**
     * 红利参数是否可用，false 时不给动作
     */
    private boolean dividendAvailable = true;

    /**
     * 红利建议摘要，逐条可读
     */
    private List<String> dividendSummary = new ArrayList<>();

    /**
     * 网格到价提醒汇总，无场内 ETF 持仓为空
     */
    private GridAdvisoryVO gridAdvisory;

    /**
     * 网格参数是否可用，false 时不给提醒
     */
    private boolean gridAvailable = true;

    /**
     * 网格提醒摘要，逐条可读
     */
    private List<String> gridSummary = new ArrayList<>();

    /**
     * 行情数据日期
     */
    private LocalDate quoteDate;

    /**
     * 股息率数据日期，取最旧一期
     */
    private LocalDate dividendDataDate;

    /**
     * 数据截止时间，正文必须显示
     */
    private LocalDate dataCutoffDate;

    private static final long serialVersionUID = 1L;

    /**
     * 是否没有任何持仓，需要改发提示邮件
     *
     * @return 无持仓返回 true
     */
    public boolean empty() {
        return varietyCount == 0;
    }

    /**
     * 单只持仓的邮件行
     */
    @Data
    public static class Item implements Serializable {

        /**
         * 品种业务主键
         */
        private String varietyKey;

        /**
         * 6 位代码
         */
        private String code;

        /**
         * 标的名称
         */
        private String name;

        /**
         * 基金类型中文名
         */
        private String category;

        /**
         * 预估分支中文名
         */
        private String branch;

        /**
         * 输出档位中文名
         */
        private String tier;

        /**
         * 可信度中文名
         */
        private String confidence;

        /**
         * 预估涨跌幅，百分点；B、C 档为空
         */
        private BigDecimal estimatedChange;

        /**
         * 涨跌方向中文名
         */
        private String direction;

        /**
         * 区间下限，百分点
         */
        private BigDecimal intervalLow;

        /**
         * 区间上限，百分点
         */
        private BigDecimal intervalHigh;

        /**
         * 持仓覆盖率，百分点
         */
        private BigDecimal coverage;

        /**
         * 是否可预估
         */
        private boolean estimable;

        /**
         * 不可预估原因
         */
        private String unavailableReason;

        private static final long serialVersionUID = 1L;
    }
}
