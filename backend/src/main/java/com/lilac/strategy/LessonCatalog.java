package com.lilac.strategy;

import com.lilac.enums.LessonPattern;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 历史教训文案，把使用者确认过的原话固化下来，提醒里必须出现可读说明而不只是规则编号
 */
public final class LessonCatalog {

    private static final Map<LessonPattern, String> TEXTS = new LinkedHashMap<>();

    static {
        TEXTS.put(LessonPattern.CHASE_HIGH,
                "你记录过「追高被套」：高位买入后价格回落被套。这次买入价已接近近期高点，"
                        + "先想清楚如果回落，自己能不能拿住");
        TEXTS.put(LessonPattern.BLIND_SWITCH,
                "你记录过「盲目转红利」：债券 6-7 月一个月没涨，8 月初没了解品种就换进红利，结果亏了。"
                        + "换仓前先确认换入的那只是什么、跟踪什么指数");
        TEXTS.put(LessonPattern.PANIC_SELL,
                "你记录过「连续亏有点难受想卖」：连续亏损时割肉，事后看多是卖在低点。"
                        + "先对照一下这次卖出是计划的一部分，还是只是难受");
    }

    /**
     * 取某类错误模式的教训说明
     *
     * @param pattern 错误模式
     * @return 说明文案，未知模式返回通用提示
     */
    public static String textOf(LessonPattern pattern) {
        if (pattern == null) {
            return "";
        }
        return TEXTS.getOrDefault(pattern, pattern.getLabel());
    }

    private LessonCatalog() {
    }
}
