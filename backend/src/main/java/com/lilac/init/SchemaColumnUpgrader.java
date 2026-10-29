package com.lilac.init;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表结构升级，为已存在的旧表补上新增列，schema.sql 的 IF NOT EXISTS 管不到加列
 */
@Slf4j
@Component
public class SchemaColumnUpgrader implements ApplicationRunner {

    private static final Map<String, Map<String, String>> REQUIRED_COLUMNS = new LinkedHashMap<>();

    private static final Map<String, List<String>> NULLABLE_COLUMNS = new LinkedHashMap<>();

    static {
        Map<String, String> errorColumns = new LinkedHashMap<>();
        errorColumns.put("thresholdValue", "DECIMAL(12, 6) DEFAULT NULL COMMENT '本次判定所用阈值，百分点；混合型为 null'");
        errorColumns.put("directionHit", "TINYINT(1) DEFAULT NULL COMMENT '方向是否命中，混合型统计用'");
        errorColumns.put("inInterval", "TINYINT(1) DEFAULT NULL COMMENT '实际值是否落在所给区间内，混合型统计用'");
        errorColumns.put("intervalWidth", "DECIMAL(12, 6) DEFAULT NULL COMMENT '所给区间宽度，百分点，混合型统计用'");
        errorColumns.put("holdingCutoffDate", "DATE DEFAULT NULL COMMENT '当时持仓截止日'");
        errorColumns.put("holdingDisclosureDate", "DATE DEFAULT NULL COMMENT '当时持仓披露日'");
        errorColumns.put("estimateSource", "VARCHAR(16) DEFAULT NULL COMMENT '预估来源 SNAPSHOT / RECOMPUTED'");
        REQUIRED_COLUMNS.put("estimate_error", errorColumns);
        // 混合型只给方向与区间时没有点估计，这两列必须允许为空，否则只能写假数字
        NULLABLE_COLUMNS.put("estimate_error", List.of("estimatedChange", "errorValue"));

        Map<String, String> netWorthColumns = new LinkedHashMap<>();
        netWorthColumns.put("accumulatedNetWorth",
                "DECIMAL(16, 6) DEFAULT NULL COMMENT '累计净值，含历史现金分红，上游不提供时为 null'");
        REQUIRED_COLUMNS.put("fund_net_worth", netWorthColumns);
    }

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 逐表补齐缺失列并把该放开可空的列改回可空，只加列不改语义，可重复执行
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        REQUIRED_COLUMNS.forEach(this::upgrade);
        NULLABLE_COLUMNS.forEach(this::relaxNotNull);
    }

    /**
     * 补齐一张表的缺失列，缺失时才执行 ALTER
     *
     * @param table   表名
     * @param columns 列名到列定义
     */
    private void upgrade(String table, Map<String, String> columns) {
        if (!tableExists(table)) {
            return;
        }
        columns.forEach((column, definition) -> {
            if (columnExists(table, column)) {
                return;
            }
            // 旧表补列只能走 ALTER，MySQL 8 不支持 ADD COLUMN IF NOT EXISTS
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("表结构升级：{}.{} 已补齐", table, column);
        });
    }

    /**
     * 把指定列改为可空，混合型无点估计时必须有地方放 null
     *
     * @param table   表名
     * @param columns 列名
     */
    private void relaxNotNull(String table, List<String> columns) {
        if (!tableExists(table)) {
            return;
        }
        for (String column : columns) {
            if (!columnExists(table, column) || isNullable(table, column)) {
                continue;
            }
            jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column + " DECIMAL(12, 6) DEFAULT NULL");
            log.info("表结构升级：{}.{} 已改为可空", table, column);
        }
    }

    /**
     * 查询 information_schema 判断表是否存在
     *
     * @param table 表名
     * @return 存在返回 true
     */
    private boolean tableExists(String table) {
        List<Integer> rows = jdbcTemplate.queryForList(
                "select 1 from information_schema.tables where table_schema = database() and table_name = ?",
                Integer.class, table);
        return !rows.isEmpty();
    }

    /**
     * 查询 information_schema 判断列是否存在
     *
     * @param table  表名
     * @param column 列名
     * @return 存在返回 true
     */
    private boolean columnExists(String table, String column) {
        List<Integer> rows = jdbcTemplate.queryForList(
                "select 1 from information_schema.columns where table_schema = database()"
                        + " and table_name = ? and column_name = ?",
                Integer.class, table, column);
        return !rows.isEmpty();
    }

    /**
     * 查询 information_schema 判断列是否已可空
     *
     * @param table  表名
     * @param column 列名
     * @return 可空返回 true
     */
    private boolean isNullable(String table, String column) {
        List<String> rows = jdbcTemplate.queryForList(
                "select is_nullable from information_schema.columns where table_schema = database()"
                        + " and table_name = ? and column_name = ?",
                String.class, table, column);
        return !rows.isEmpty() && "YES".equalsIgnoreCase(rows.get(0));
    }
}
