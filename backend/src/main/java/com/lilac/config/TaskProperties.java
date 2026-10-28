package com.lilac.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 四时点调度配置，时点、补跑时效、时钟跳变阈值与节假日清单都在这里
 */
@Data
@Component
@ConfigurationProperties(prefix = "fund.task")
public class TaskProperties {

    /**
     * 定时任务总开关，关闭后定时触发与启动补跑都不执行，手动触发仍可用
     */
    private boolean enabled = true;

    /**
     * 定时器开关，关闭后四个时点不再自动触发，手动触发与页面查询仍可用
     */
    private boolean schedulerEnabled = true;

    /**
     * 调度时区，服务器时区与使用者所在地不一致时靠它对齐
     */
    private String zone = "Asia/Shanghai";

    /**
     * 补跑时效窗口，分钟；时点已过且超过该窗口视为过期不再补发
     */
    private int catchUpWindowMinutes = 120;

    /**
     * 时钟跳变阈值，秒；墙钟与单调时钟的偏差超过该值即判为跳变
     */
    private int clockJumpThresholdSeconds = 300;

    /**
     * 跳变保护时长，分钟；跳变发生后该时长内的时点一律跳过并留痕
     */
    private int clockJumpGuardMinutes = 10;

    /**
     * 巡检 cron，补齐定时器漏掉的时点；重启跨过时点由它和启动补跑共同兜住
     */
    private String sweepCron = "0 5 * * * *";

    /**
     * 额外节假日清单，格式 yyyy-MM-dd，周末之外的休市日在此补充
     */
    private List<String> holidays = new ArrayList<>();

    /**
     * 四个时点的 cron 与应发时刻，键为 SendPoint 名称
     */
    private Map<String, PointProperties> points = new LinkedHashMap<>();

    /**
     * 单时点配置
     */
    @Data
    public static class PointProperties {

        /**
         * cron 表达式，分钟与小时写成整数时可直接推出应发时刻
         */
        private String cron;

        /**
         * 应发时刻 HH:mm，仅在 cron 无法解出确定时刻时使用
         */
        private String time;

        /**
         * 单时点开关，关闭后该时点不触发也不出现在总览里
         */
        private boolean enabled = true;
    }
}
