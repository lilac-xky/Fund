package com.lilac.enums;

import lombok.Getter;

/**
 * 预估可信度，由持仓覆盖率与历史误差共同判定，不按披露距今月数衰减
 */
@Getter
public enum EstimateConfidence {

    /**
     * 高，覆盖率与历史误差都好
     */
    HIGH("高"),

    /**
     * 中，覆盖率或历史误差有一项偏弱
     */
    MEDIUM("中"),

    /**
     * 低，覆盖率不足或历史误差偏大
     */
    LOW("低");

    /**
     * 中文名称，供页面与接口回显
     */
    private final String label;

    EstimateConfidence(String label) {
        this.label = label;
    }
}
