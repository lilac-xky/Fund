package com.lilac.adapter.fund;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对标指数登记表，把基金名称或跟踪标的里的指数名折算成指数代码
 */
public final class BenchmarkIndexRegistry {

    private static final Map<String, String> INDEX_BY_NAME = new LinkedHashMap<>();

    static {
        // 顺序敏感：先长后短，避免「沪深300」被更短的词抢走
        INDEX_BY_NAME.put("中证红利低波动100", "930955");
        INDEX_BY_NAME.put("中证红利低波动", "H30269");
        INDEX_BY_NAME.put("中证主要消费", "000932");
        INDEX_BY_NAME.put("中证医药卫生", "000933");
        INDEX_BY_NAME.put("全指医药", "000991");
        INDEX_BY_NAME.put("中证新能源", "399808");
        INDEX_BY_NAME.put("沪深300", "000300");
        INDEX_BY_NAME.put("中证500", "000905");
        INDEX_BY_NAME.put("中证1000", "000852");
        INDEX_BY_NAME.put("上证50", "000016");
        INDEX_BY_NAME.put("创业板", "399006");
        INDEX_BY_NAME.put("科创50", "000688");
        INDEX_BY_NAME.put("中证白酒", "399997");
        INDEX_BY_NAME.put("中证军工", "399967");
        INDEX_BY_NAME.put("证券公司", "399975");
        INDEX_BY_NAME.put("中证银行", "399986");
        INDEX_BY_NAME.put("中证红利", "000922");
        INDEX_BY_NAME.put("上证红利", "000015");
        INDEX_BY_NAME.put("深证红利", "399324");
        INDEX_BY_NAME.put("上证指数", "000001");
    }

    private BenchmarkIndexRegistry() {
    }

    /**
     * 按基金名称或跟踪标的名称匹配指数代码，未收录时返回 null
     *
     * @param name 基金简称或跟踪标的名称
     * @return 指数代码，未命中返回 null
     */
    public static String findByFundName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.replace("指数", "").replace("全收益", "")
                .replace("ETF", "").replace("联接", "").replace(" ", "").toUpperCase();
        for (Map.Entry<String, String> entry : INDEX_BY_NAME.entrySet()) {
            if (normalized.contains(entry.getKey().toUpperCase())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
