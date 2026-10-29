package com.lilac.adapter.fund;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 字母指数到可交易 ETF 的代理登记表，字母开头的指数代码没有行情，只能用跟踪它的 ETF 代替
 */
public final class IndexEtfRegistry {

    private static final Map<String, String> ETF_BY_INDEX = new LinkedHashMap<>();

    static {
        // 只收录已核实「该 ETF 确实跟踪该指数」的字母代码；纯数字指数仍按号段推断，不进此表
        ETF_BY_INDEX.put("H30269", "512890");
    }

    private IndexEtfRegistry() {
    }

    /**
     * 按字母指数代码取跟踪它的 ETF 代码，未收录时返回 null
     *
     * @param indexCode 指数代码，如 H30269
     * @return ETF 代码，未收录返回 null
     */
    public static String findByIndexCode(String indexCode) {
        if (indexCode == null || indexCode.isBlank()) {
            return null;
        }
        return ETF_BY_INDEX.get(indexCode.trim().toUpperCase());
    }
}
