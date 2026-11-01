package com.lilac.service.impl;

import com.lilac.domain.entity.Holding;
import com.lilac.domain.entity.HoldingChange;
import com.lilac.domain.entity.Variety;
import com.lilac.domain.vo.DuplicateCleanVO;
import com.lilac.enums.HoldingChangeType;
import com.lilac.mapper.HoldingChangeMapper;
import com.lilac.mapper.HoldingMapper;
import com.lilac.mapper.VarietyMapper;
import com.lilac.service.DuplicateCleanService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 重复数据清理实现：识别「同品种 + 同份额 + 同成本价 + 同建仓日」的重复批次并保留最早一条
 */
@Slf4j
@Service
public class DuplicateCleanServiceImpl implements DuplicateCleanService {

    @Resource
    private HoldingMapper holdingMapper;

    @Resource
    private HoldingChangeMapper holdingChangeMapper;

    @Resource
    private VarietyMapper varietyMapper;

    /**
     * 预览重复批次，按品种聚合后给出每组保留哪条、删哪几条
     *
     * @return 重复组合列表
     */
    @Override
    public List<DuplicateCleanVO> preview() {
        Map<String, Variety> varieties = varietyMap();
        return duplicates().entrySet().stream().map(entry -> {
            List<Holding> rows = entry.getValue();
            // 保留最早创建的那条，删掉其余
            rows.sort(Comparator.comparing(Holding::getId));
            Holding keep = rows.get(0);
            DuplicateCleanVO vo = new DuplicateCleanVO();
            vo.setVarietyKey(keep.getVarietyKey());
            Variety variety = varieties.get(keep.getVarietyKey());
            vo.setVarietyName(variety == null ? keep.getVarietyKey() : variety.getVarietyName());
            vo.setVarietyCode(variety == null ? null : variety.getVarietyCode());
            vo.setShares(keep.getShares());
            vo.setCostPrice(keep.getCostPrice());
            vo.setOpenDate(keep.getOpenDate());
            vo.setCount(rows.size());
            vo.setKeepId(keep.getId());
            vo.setRemoveIds(rows.stream().skip(1).map(row -> String.valueOf(row.getId()))
                    .collect(Collectors.joining(",")));
            return vo;
        }).toList();
    }

    /**
     * 执行清理：删重复批次并同步去重变动记录
     *
     * @return 清理结果说明
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String clean() {
        Map<String, List<Holding>> duplicated = duplicates();
        int removedBatches = 0;
        for (List<Holding> rows : duplicated.values()) {
            rows.sort(Comparator.comparing(Holding::getId));
            for (Holding row : rows.subList(1, rows.size())) {
                holdingMapper.deleteById(row.getId());
                removedBatches++;
            }
        }
        // 变动记录里同样的重复也一起去掉，否则历史还会显示重复行
        int removedChanges = dedupeChanges();
        String result = "已删除重复批次 " + removedBatches + " 条、重复变动记录 " + removedChanges + " 条";
        log.info(result);
        return result;
    }

    /**
     * 找重复批次：同一品种下份额、成本价与建仓日三项全同
     *
     * @return 重复组合，键为「品种|份额|成本价|日期」
     */
    private Map<String, List<Holding>> duplicates() {
        List<Holding> all = holdingMapper.selectListByQuery(QueryWrapper.create()
                .eq(Holding::getStatus, com.lilac.enums.HoldingStatus.HOLDING)
                .orderBy(Holding::getId, true));
        Map<String, List<Holding>> grouped = new LinkedHashMap<>();
        for (Holding row : all) {
            grouped.computeIfAbsent(signatureOf(row), key -> new ArrayList<>()).add(row);
        }
        Map<String, List<Holding>> result = new LinkedHashMap<>();
        grouped.forEach((key, rows) -> {
            if (rows.size() > 1) {
                result.put(key, rows);
            }
        });
        return result;
    }

    /**
     * 拼批次的去重签名，数值统一到四位小数避免精度差异被当成不同
     *
     * @param holding 批次
     * @return 签名
     */
    private String signatureOf(Holding holding) {
        return holding.getVarietyKey() + "|" + plain(holding.getShares()) + "|"
                + plain(holding.getCostPrice()) + "|" + holding.getOpenDate();
    }

    /**
     * 变动记录去重：同品种同类型同份额同净值同日期只留最早一条
     *
     * @return 删除条数
     */
    private int dedupeChanges() {
        List<HoldingChange> all = holdingChangeMapper.selectListByQuery(QueryWrapper.create()
                .orderBy(HoldingChange::getId, true));
        Map<String, List<HoldingChange>> grouped = new LinkedHashMap<>();
        for (HoldingChange change : all) {
            grouped.computeIfAbsent(changeSignatureOf(change), key -> new ArrayList<>()).add(change);
        }
        int removed = 0;
        for (List<HoldingChange> rows : grouped.values()) {
            if (rows.size() <= 1) {
                continue;
            }
            for (HoldingChange row : rows.subList(1, rows.size())) {
                holdingChangeMapper.deleteById(row.getId());
                removed++;
            }
        }
        return removed;
    }

    /**
     * 拼变动记录的去重签名
     *
     * @param change 变动记录
     * @return 签名
     */
    private String changeSignatureOf(HoldingChange change) {
        HoldingChangeType type = change.getChangeType();
        return change.getVarietyKey() + "|" + type + "|" + plain(change.getShares()) + "|"
                + plain(change.getUnitPrice()) + "|" + change.getOpenDate();
    }

    /**
     * 数值转文本，空值给空串，统一四位小数
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        return value == null ? "" : value.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 品种主键到品种，用于带出名称
     *
     * @return 品种映射
     */
    private Map<String, Variety> varietyMap() {
        return varietyMapper.selectListByQuery(QueryWrapper.create()).stream()
                .filter(item -> item.getVarietyKey() != null)
                .collect(Collectors.toMap(Variety::getVarietyKey, item -> item, (left, right) -> left));
    }
}
