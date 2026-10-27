package com.lilac.config;

/**
 * Redis key 规范与过期时间，Redis 只承载可重建的当日行情与降级快照
 */
public final class RedisKeys {

    /**
     * 当日行情缓存前缀，结构 fund:quote:{品种类型}:{代码}
     */
    public static final String QUOTE = "fund:quote:";

    /**
     * 当日行情缓存保留时间，秒，覆盖一个完整交易日
     */
    public static final long QUOTE_TTL_SECONDS = 6 * 60 * 60L;

    /**
     * 断网降级快照前缀，结构 fund:snapshot:{快照名}:{日期}
     */
    public static final String SNAPSHOT = "fund:snapshot:";

    /**
     * 断网降级快照保留时间，秒，覆盖一个交易日加次日早盘
     */
    public static final long SNAPSHOT_TTL_SECONDS = 24 * 60 * 60L;

    /**
     * 最近一次成功取数前缀，结构 fund:fallback:{数据源}:{标的}
     */
    public static final String FALLBACK = "fund:fallback:";

    /**
     * 最近一次成功取数保留时间，秒，上游失效时供回退展示
     */
    public static final long FALLBACK_TTL_SECONDS = 72 * 60 * 60L;

    private RedisKeys() {
    }

    /**
     * 拼接当日行情缓存 key
     *
     * @param symbol 标的标识，行情缓存用完整代码（如 sh510300），页面缓存用品种业务主键（如 OF:000001）
     * @return 缓存 key
     */
    public static String quoteKey(String symbol) {
        return QUOTE + symbol;
    }

    /**
     * 拼接断网降级快照 key
     *
     * @param snapshotName 快照名，如 overview
     * @param date         日期，yyyy-MM-dd
     * @return 快照 key
     */
    public static String snapshotKey(String snapshotName, String date) {
        return SNAPSHOT + snapshotName + ":" + date;
    }

    /**
     * 拼接最近一次成功取数 key
     *
     * @param source 数据源名，如 eastmoney
     * @param target 标的名，如 510300
     * @return 回退 key
     */
    public static String fallbackKey(String source, String target) {
        return FALLBACK + source + ":" + target;
    }
}
