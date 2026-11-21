package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 红利阶梯参数视图，按标的给出，附问题清单供设置页提示不合法阶梯
 */
@Data
public class DividendPlanVO implements Serializable {

    /**
     * 所属品种业务主键
     */
    private String varietyKey;

    /**
     * 所属品种名称，供页面显示这套参数管的是哪只
     */
    private String varietyName;

    /**
     * 跟踪指数代码，页面据此说明股息率来自哪只指数
     */
    private String indexCode;

    /**
     * 跟踪指数全称
     */
    private String indexName;

    /**
     * 股息率阶梯，按股息率降序
     */
    private List<LadderRung> ladder;

    /**
     * 仓位上限，即最高档的应持有金额，单位元
     */
    private BigDecimal topAmount;

    /**
     * 保底底仓，即最低档的应持有金额，单位元
     */
    private BigDecimal bottomAmount;

    /**
     * 参数是否齐全且自洽
     */
    private Boolean usable;

    /**
     * 参数问题清单，可用时为空
     */
    private List<String> issues;

    /**
     * 参数提示，如「旧版单阈值方式已停用」
     */
    private List<String> notes;

    private static final long serialVersionUID = 1L;

    /**
     * 一档：股息率门槛与该档的应持有金额
     */
    @Data
    public static class LadderRung implements Serializable {

        /**
         * 股息率门槛，单位百分点
         */
        private BigDecimal yield;

        /**
         * 该档的应持有金额，单位元
         */
        private BigDecimal amount;

        private static final long serialVersionUID = 1L;
    }
}
