package com.lilac.adapter.probe;

import com.lilac.domain.dto.source.SourceEventQueryRequest;
import com.lilac.domain.entity.SourceEventRecord;
import com.lilac.domain.vo.SourceFailureTargetVO;
import com.lilac.domain.vo.SourceStatVO;
import com.lilac.mapper.SourceEventMapper;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源取数留痕器，记录成功与失败事件并汇总各源的健康度
 */
@Slf4j
@Component
public class SourceEventRecorder {

    /**
     * 失败原因列宽，异常堆栈没必要整段入库
     */
    private static final int MESSAGE_LIMIT = 500;

    @Value("${fund.adapter.probe.enabled:true}")
    private boolean enabled;

    @Value("${fund.adapter.probe.keep-days:90}")
    private int keepDays;

    @Resource
    private SourceEventMapper sourceEventMapper;

    /**
     * 记一条成功事件，留痕关闭时直接返回
     *
     * @param source 数据源名
     * @param target 取数目标
     */
    public void success(String source, String target) {
        record(source, target, Boolean.TRUE, null, null);
    }

    /**
     * 记一条失败事件，留痕失败不影响取数本身
     *
     * @param source      数据源名
     * @param target      取数目标
     * @param failureKind 失败类型，见 {@link FailureKind}
     * @param message     原因
     */
    public void failure(String source, String target, String failureKind, String message) {
        record(source, target, Boolean.FALSE, failureKind, message);
    }

    /**
     * 写一条事件
     *
     * @param source      数据源名
     * @param target      取数目标
     * @param success     是否成功
     * @param failureKind 失败类型
     * @param message     说明
     */
    private void record(String source, String target, Boolean success, String failureKind, String message) {
        if (!enabled) {
            return;
        }
        try {
            SourceEventRecord row = new SourceEventRecord();
            row.setSource(trim(source, 32));
            row.setTarget(trim(target, 64));
            row.setSuccess(success);
            row.setFailureKind(failureKind);
            row.setMessage(trim(message, MESSAGE_LIMIT));
            row.setHappenedAt(LocalDateTime.now());
            sourceEventMapper.insertSelective(row);
        } catch (RuntimeException e) {
            // 留痕是诊断辅助，写失败不能反过来让取数失败
            log.warn("取数留痕写入失败 source={} target={}：{}", source, target, e.getMessage());
        }
    }

    /**
     * 汇总指定日期区间内各数据源的成败次数
     *
     * @param request 查询请求
     * @return 各源统计，按失败次数降序
     */
    public List<SourceStatVO> stats(SourceEventQueryRequest request) {
        SourceEventQueryRequest query = request == null ? new SourceEventQueryRequest() : request;
        LocalDate start = query.getStartDate() == null ? LocalDate.now().minusDays(7) : query.getStartDate();
        LocalDate end = query.getEndDate() == null ? LocalDate.now() : query.getEndDate();
        List<SourceEventRecord> rows = sourceEventMapper.selectListByQuery(QueryWrapper.create()
                .ge(SourceEventRecord::getHappenedAt, start.atStartOfDay())
                .lt(SourceEventRecord::getHappenedAt, end.plusDays(1).atStartOfDay()));
        Map<String, SourceStatVO> grouped = new LinkedHashMap<>();
        // 按源 + 目标聚合失败明细，页面需要看到「哪个代码在失败」而不只是一个总数
        Map<String, SourceFailureTargetVO> targetFailures = new LinkedHashMap<>();
        for (SourceEventRecord row : rows) {
            SourceStatVO stat = grouped.computeIfAbsent(row.getSource(), key -> {
                SourceStatVO vo = new SourceStatVO();
                vo.setSource(key);
                vo.setTotalCount(0);
                vo.setSuccessCount(0);
                vo.setFailureCount(0);
                vo.setNoDataCount(0);
                vo.setFailures(new ArrayList<>());
                vo.setFailureTargets(new ArrayList<>());
                return vo;
            });
            stat.setTotalCount(stat.getTotalCount() + 1);
            if (Boolean.TRUE.equals(row.getSuccess())) {
                stat.setSuccessCount(stat.getSuccessCount() + 1);
            } else if (FailureKind.NO_DATA.equals(row.getFailureKind())) {
                // 无数据是「标的本身取不到」，不是源坏了，单独计数不进失败率
                stat.setNoDataCount(stat.getNoDataCount() + 1);
            } else {
                stat.setFailureCount(stat.getFailureCount() + 1);
                String key = row.getSource() + "|" + row.getTarget() + "|" + row.getFailureKind();
                SourceFailureTargetVO detail = targetFailures.computeIfAbsent(key, ignored -> {
                    SourceFailureTargetVO vo = new SourceFailureTargetVO();
                    vo.setSource(row.getSource());
                    vo.setTarget(row.getTarget());
                    vo.setFailureKind(row.getFailureKind());
                    vo.setCount(0);
                    return vo;
                });
                detail.setCount(detail.getCount() + 1);
                detail.setLatestMessage(row.getMessage());
                detail.setLatestAt(row.getHappenedAt());
                if (row.getFailureKind() != null && !stat.getFailures().contains(row.getFailureKind())) {
                    stat.getFailures().add(row.getFailureKind());
                }
            }
            stat.setLastHappenedAt(row.getHappenedAt());
        }
        // 失败明细挂回各源并按次数降序，页面取前几名展示
        for (SourceFailureTargetVO detail : targetFailures.values()) {
            SourceStatVO stat = grouped.get(detail.getSource());
            if (stat != null) {
                stat.getFailureTargets().add(detail);
            }
        }
        List<SourceStatVO> stats = new ArrayList<>(grouped.values());
        stats.forEach(stat -> {
            fillRate(stat);
            stat.getFailureTargets().sort((left, right) -> Integer.compare(right.getCount(), left.getCount()));
        });
        stats.sort((left, right) -> Integer.compare(right.getFailureCount(), left.getFailureCount()));
        return stats;
    }

    /**
     * 清掉超过保留期的事件，供启动自检调用
     *
     * @return 删除行数
     */
    public int purgeExpired() {
        if (keepDays <= 0) {
            return 0;
        }
        LocalDateTime before = LocalDate.now().minusDays(keepDays).atStartOfDay();
        int deleted = sourceEventMapper.deleteByQuery(QueryWrapper.create()
                .lt(SourceEventRecord::getHappenedAt, before));
        if (deleted > 0) {
            log.info("取数留痕清理完成，删除 {} 条早于 {} 的记录", deleted, before);
        }
        return deleted;
    }

    /**
     * 计算失败率，只按真失败（结构变化/限流/网络）除以总次数，保留两位小数
     *
     * @param stat 统计
     */
    private void fillRate(SourceStatVO stat) {
        if (stat.getTotalCount() == null || stat.getTotalCount() == 0) {
            stat.setFailureRate(BigDecimal.ZERO);
            return;
        }
        stat.setFailureRate(BigDecimal.valueOf(stat.getFailureCount())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(stat.getTotalCount()), 2, RoundingMode.HALF_UP));
    }

    /**
     * 截断字符串到指定长度，null 返回 null
     *
     * @param value 原值
     * @param limit 上限
     * @return 截断后的值
     */
    private String trim(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    /**
     * 失败类型，用于区分「上游改版」与「网络抖动」
     */
    public static final class FailureKind {

        /**
         * 上游返回结构变化，关键字段缺失
         */
        public static final String STRUCTURE = "STRUCTURE";

        /**
         * 网络或 HTTP 层失败
         */
        public static final String NETWORK = "NETWORK";

        /**
         * 上游返回无数据，但结构正常
         */
        public static final String NO_DATA = "NO_DATA";

        /**
         * 上游限流或返回验证页
         */
        public static final String THROTTLED = "THROTTLED";

        private FailureKind() {
        }
    }
}
