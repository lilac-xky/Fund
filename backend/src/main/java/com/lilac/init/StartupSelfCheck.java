package com.lilac.init;

import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.logicdelete.impl.DefaultLogicDeleteProcessor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动自检，确认建表与逻辑删除配置均生效
 */
@Slf4j
@Component
public class StartupSelfCheck implements ApplicationRunner {

    private static final List<String> REQUIRED_TABLES =
            List.of("variety", "holding", "strategy_param", "estimate_error", "send_record");

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 校验五张业务表均已建好，并确认逻辑删除处理器已生效
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        checkTables();
        checkLogicDelete();
    }

    /**
     * 逐张表校验存在性，缺失时抛出异常中断启动
     */
    private void checkTables() {
        List<String> missing = REQUIRED_TABLES.stream()
                .filter(table -> !tableExists(table))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("业务表缺失，请检查 spring.sql.init 与 db/schema.sql：" + missing);
        }
        log.info("数据表自检通过，共 {} 张", REQUIRED_TABLES.size());
    }

    /**
     * 校验逻辑删除处理器，配置漂移会导致删除退化为物理删除
     */
    private void checkLogicDelete() {
        if (!(LogicDeleteManager.getProcessor() instanceof DefaultLogicDeleteProcessor)) {
            throw new IllegalStateException("逻辑删除处理器异常: " + LogicDeleteManager.getProcessor());
        }
        log.info("逻辑删除自检通过");
    }

    /**
     * 查询 information_schema 判断表是否存在
     *
     * @param tableName 表名
     * @return 存在返回 true
     */
    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }
}
