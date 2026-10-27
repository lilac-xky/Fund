package com.lilac.adapter.fund;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 红利指数登记表，只收录已核实可用的中证与深证系列指数
 */
public final class DividendIndexRegistry {

    private static final Map<String, String> INDEX_BY_NAME = new LinkedHashMap<>();

    static {
        // 顺序敏感：更长的名称先匹配，避免「红利低波」抢走「红利低波100」
        INDEX_BY_NAME.put("中证红利低波动100", "930955");
        INDEX_BY_NAME.put("红利低波100", "930955");
        INDEX_BY_NAME.put("中证红利低波动", "H30269");
        INDEX_BY_NAME.put("红利低波", "H30269");
        INDEX_BY_NAME.put("中证红利", "000922");
        INDEX_BY_NAME.put("上证红利", "000015");
        INDEX_BY_NAME.put("深证红利", "399324");
        INDEX_BY_NAME.put("国证红利", "399321");
    }

    private DividendIndexRegistry() {
    }

    /**
     * 按跟踪标的全称匹配指数代码，未收录时返回 null
     *
     * @param trackIndexName 跟踪标的名称，如 中证红利低波动指数
     * @return 指数代码，未命中返回 null
     */
    public static String findByTrackIndexName(String trackIndexName) {
        if (trackIndexName == null || trackIndexName.isBlank()) {
            return null;
        }
        String normalized = trackIndexName.replace("指数", "").replace("全收益", "").replace(" ", "").trim();
        for (Map.Entry<String, String> entry : INDEX_BY_NAME.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
