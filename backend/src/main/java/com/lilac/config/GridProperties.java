package com.lilac.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * 网格判断的时点配置，场外基金只在尾盘按预估净值判断
 */
@Data
@Component
@ConfigurationProperties(prefix = "fund.grid")
public class GridProperties {

    /**
     * 场外基金可以开始判断的时刻，默认 14:00；早于该时刻一律不给场外网格提醒
     */
    private LocalTime tailWindowStart = LocalTime.of(14, 0);
}
