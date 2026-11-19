package com.lilac.service.impl;

import com.lilac.config.TaskProperties;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.EstimateSnapshotVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.engine.model.NetWorthReadiness;
import com.lilac.service.EstimateSnapshotService;
import com.lilac.service.FundNetWorthService;
import com.lilac.service.NetWorthReadinessService;
import com.lilac.service.VarietyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 晚间净值就绪判定实现，只把当日真的给出过预估的基金算作「该出净值」的对象
 */
@Slf4j
@Service
public class NetWorthReadinessServiceImpl implements NetWorthReadinessService {

    @Resource
    private EstimateSnapshotService estimateSnapshotService;

    @Resource
    private FundNetWorthService fundNetWorthService;

    @Resource
    private VarietyService varietyService;

    @Resource
    private com.lilac.config.TaskProperties taskProperties;

    /**
     * 晚间发信的截止时刻兜底值，配置缺省时用 23:00
     */
    private static final String DEFAULT_DEADLINE = "23:00";

    /**
     * 判定净值是否已到齐，只看当日有可对照预估的基金；本来就无法预估的一律不参与等待
     *
     * @param tradeDate 交易日
     * @param now       当前时刻
     * @return 就绪判定
     */
    @Override
    public NetWorthReadiness check(LocalDate tradeDate, LocalDateTime now) {
        NetWorthReadiness readiness = new NetWorthReadiness();
        Map<String, EstimateSnapshotVO> snapshots = evaluableSnapshots(tradeDate);
        readiness.setExpectedCount(snapshots.size());
        if (snapshots.isEmpty()) {
            // 没有可对照的预估就没什么可等的，直接发一封说明情况的晚间邮件
            readiness.setReady(true);
            readiness.setShouldWait(false);
            readiness.setNote("当日没有可对照的预估输出，晚间邮件直接发出");
            return readiness;
        }
        Map<String, VarietyVO> varietyMap = varietyMap(snapshots.keySet());
        List<NetWorthReadiness.Pending> pending = new ArrayList<>();
        for (String varietyKey : snapshots.keySet()) {
            VarietyVO variety = varietyMap.get(varietyKey);
            if (variety == null || variety.getVarietyCode() == null || variety.getVarietyCode().isBlank()) {
                continue;
            }
            LocalDate latest = latestNetWorthDate(variety.getVarietyCode());
            if (latest != null && !latest.isBefore(tradeDate)) {
                continue;
            }
            NetWorthReadiness.Pending item = new NetWorthReadiness.Pending();
            item.setCode(variety.getVarietyCode());
            item.setName(variety.getVarietyName());
            item.setLatestDate(latest == null ? null : latest.toString());
            pending.add(item);
        }
        readiness.setArrivedCount(snapshots.size() - pending.size());
        readiness.setPending(pending);
        boolean deadlineReached = deadlineReached(now);
        readiness.setReady(pending.isEmpty() || deadlineReached);
        // 已过截止时刻就不再阻塞，否则一只长期停牌的基金会把整封邮件永远拖住
        readiness.setShouldWait(pending.isEmpty() ? false : !deadlineReached);
        readiness.setNote(noteOf(readiness, deadlineReached));
        return readiness;
    }

    /**
     * 拼一句话说明，到齐、等待中、已过截止三种情形各说各的
     *
     * @param readiness        就绪判定
     * @param deadlineReached  是否已过截止时刻
     * @return 说明文字
     */
    private String noteOf(NetWorthReadiness readiness, boolean deadlineReached) {
        if (readiness.getPending().isEmpty()) {
            return "净值已全部到齐（" + readiness.getExpectedCount() + " 只），可以发晚间邮件";
        }
        String detail = "还差 " + readiness.getPending().size() + " 只未出净值";
        if (deadlineReached) {
            return detail + "，已过截止时刻 " + deadline() + "，按当前缺口发送";
        }
        return detail + "，" + readiness.getArrivedCount() + "/" + readiness.getExpectedCount()
                + " 已到齐，等待中（截止 " + deadline() + "）";
    }

    /**
     * 判断是否已到截止时刻，解析失败时按已到处理，宁可早发也不永远不发
     *
     * @param now 当前时刻
     * @return 已到截止返回 true
     */
    private boolean deadlineReached(LocalDateTime now) {
        LocalTime limit;
        try {
            limit = LocalTime.parse(deadline());
        } catch (RuntimeException e) {
            log.warn("晚间截止时刻配置非法，按已到截止处理：{}", deadline());
            return true;
        }
        return !now.toLocalTime().isBefore(limit);
    }

    /**
     * 取配置里的晚间截止时刻，未配置时用默认值
     *
     * @return HH:mm 文本
     */
    private String deadline() {
        TaskProperties.PointProperties evening =
                taskProperties.getPoints() == null ? null : taskProperties.getPoints().get("EVENING");
        String configured = evening == null ? null : evening.getDeadline();
        return configured == null || configured.isBlank() ? DEFAULT_DEADLINE : configured.trim();
    }

    /**
     * 取当日有可对照输出的预估留存，无法预估的基金不进等待队列
     *
     * @param tradeDate 交易日
     * @return 品种业务主键到留存的映射
     */
    private Map<String, EstimateSnapshotVO> evaluableSnapshots(LocalDate tradeDate) {
        Map<String, EstimateSnapshotVO> map = new LinkedHashMap<>();
        for (EstimateSnapshotVO snapshot : estimateSnapshotService.listByDate(tradeDate)) {
            // 本来就无法预估的基金不会有净值对照，等它只会让整封邮件发不出去
            if (!Boolean.TRUE.equals(snapshot.getEvaluable())) {
                continue;
            }
            if (snapshot.getVarietyKey() == null || snapshot.getVarietyKey().isBlank()) {
                continue;
            }
            map.put(snapshot.getVarietyKey(), snapshot);
        }
        return map;
    }

    /**
     * 按业务主键批量取品种，用于拼未出净值的代码与名称
     *
     * @param varietyKeys 品种业务主键
     * @return 品种映射
     */
    private Map<String, VarietyVO> varietyMap(Set<String> varietyKeys) {
        Map<String, VarietyVO> map = new LinkedHashMap<>();
        if (varietyKeys.isEmpty()) {
            return map;
        }
        List<VarietyVO> varieties = varietyService.listVariety(new VarietyQueryRequest());
        for (VarietyVO variety : varieties) {
            if (varietyKeys.contains(variety.getVarietyKey())) {
                map.put(variety.getVarietyKey(), variety);
            }
        }
        return map;
    }

    /**
     * 取库内最新净值日期
     *
     * @param fundCode 6 位基金代码
     * @return 最新净值日期，无数据返回 null
     */
    private LocalDate latestNetWorthDate(String fundCode) {
        try {
            return fundNetWorthService.latestDate(fundCode);
        } catch (RuntimeException e) {
            log.warn("读取最新净值日期失败 code={}：{}", fundCode, e.getMessage());
            return null;
        }
    }
}
