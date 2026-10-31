package com.lilac.enums;

/**
 * 策略参数分组，邮件与 SMTP 参数不进数据库
 */
public enum ParamGroup {

    /**
     * 红利策略参数
     */
    DIVIDEND,

    /**
     * 网格策略参数
     */
    GRID,

    /**
     * 债券监控参数
     */
    BOND,

    /**
     * 技术指标参数
     */
    INDICATOR,

    /**
     * 历史教训警示参数
     */
    ALERT
}
