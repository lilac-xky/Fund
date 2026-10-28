package com.lilac.enums;

import lombok.Getter;

/**
 * 任务触发来源，留痕与返回里靠它区分自动、补跑与手动
 */
@Getter
public enum TaskTrigger {

    /**
     * 到点自动触发
     */
    SCHEDULED("定时触发"),

    /**
     * 应用启动补跑或缺口扫描后触发
     */
    CATCH_UP("启动补跑"),

    /**
     * 使用者或调试手动触发
     */
    MANUAL("手动触发");

    /**
     * 中文名称，供页面回显
     */
    private final String label;

    TaskTrigger(String label) {
        this.label = label;
    }
}
