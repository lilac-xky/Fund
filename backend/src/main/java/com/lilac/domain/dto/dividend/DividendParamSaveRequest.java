package com.lilac.domain.dto.dividend;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 红利阶梯保存请求，按基金各存一套，每档写「这个股息率下该持有多少钱」
 */
@Data
public class DividendParamSaveRequest implements Serializable {

    /**
     * 品种业务主键，必填：参数按基金分开，没有标的就无处挂靠
     */
    private String varietyKey;

    /**
     * 股息率阶梯，至少两档；最低一档即保底底仓
     */
    private List<LadderRung> ladder = new ArrayList<>();

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
