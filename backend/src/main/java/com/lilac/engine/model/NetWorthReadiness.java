package com.lilac.engine.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 晚间净值就绪判定结果，告诉调度层「现在发还是再等等」以及为什么
 */
@Data
public class NetWorthReadiness implements Serializable {

    /**
     * 是否可以发信：全部待校准基金都已出净值，或已到截止时刻
     */
    private boolean ready;

    /**
     * 是否需要阻塞等待，false 时无论到没到齐都按当前结果发
     */
    private boolean shouldWait;

    /**
     * 待校准只数，只统计当日确实给出过可对照输出的基金
     */
    private int expectedCount;

    /**
     * 已出净值的只数
     */
    private int arrivedCount;

    /**
     * 仍未出净值的基金，每项带代码、名称与原因
     */
    private List<Pending> pending = new ArrayList<>();

    /**
     * 一句话说明，直接进日志与页面
     */
    private String note;

    private static final long serialVersionUID = 1L;

    /**
     * 一只尚未出净值的基金
     */
    @Data
    public static class Pending implements Serializable {

        /**
         * 6 位代码
         */
        private String code;

        /**
         * 标的名称
         */
        private String name;

        /**
         * 库内最新净值日期，用于说明停在哪一天
         */
        private String latestDate;

        private static final long serialVersionUID = 1L;
    }
}
