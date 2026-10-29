package com.lilac.service.impl;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.domain.entity.FundNetWorth;
import com.lilac.mapper.FundNetWorthMapper;
import com.lilac.service.FundNetWorthService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基金历史净值业务实现，只写库内最大净值日期之后的新点
 */
@Slf4j
@Service
public class FundNetWorthServiceImpl implements FundNetWorthService {

    private static final int BATCH_SIZE = 1000;

    @Resource
    private FundNetWorthMapper fundNetWorthMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 增量写入净值，只保留库内最大日期之后的新点，首次为全量、之后每日一条
     *
     * @param fundCode 6 位基金代码
     * @param points   净值序列
     * @return 实际新增行数
     */
    @Override
    public int saveIncremental(String fundCode, List<FundNetWorthPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0;
        }
        backfillAccumulated(fundCode, points);
        List<FundNetWorth> rows = collectNewRows(fundCode, points);
        if (rows.isEmpty()) {
            log.info("净值无需新增 code={}", fundCode);
            return 0;
        }
        // 首次全量有几千行，放同一事务里批量提交，避免逐条自动提交反复落盘
        transactionTemplate.executeWithoutResult(status -> insertBatches(rows));
        log.info("净值落库完成 code={} 新增 {} 条", fundCode, rows.size());
        return rows.size();
    }

    /**
     * 给库里已有但缺累计净值的行补上，否则历史行永远拿不到分红数据
     *
     * @param fundCode 6 位基金代码
     * @param points   上游净值序列
     */
    private void backfillAccumulated(String fundCode, List<FundNetWorthPoint> points) {
        List<FundNetWorthPoint> withAccumulated = points.stream()
                .filter(point -> point.getAccumulatedNetWorth() != null)
                .toList();
        if (withAccumulated.isEmpty() || !hasAccumulatedGap(fundCode)) {
            return;
        }
        // 一次取回该基金全部已有行的日期与累计净值，避免逐点查库
        Map<LocalDate, Long> existingIds = new LinkedHashMap<>();
        fundNetWorthMapper.selectListByQuery(QueryWrapper.create()
                        .select(FundNetWorth::getId, FundNetWorth::getNetWorthDate,
                                FundNetWorth::getAccumulatedNetWorth)
                        .eq(FundNetWorth::getFundCode, fundCode))
                .forEach(row -> existingIds.put(row.getNetWorthDate(), row.getId()));
        int backfilled = 0;
        for (FundNetWorthPoint point : withAccumulated) {
            Long id = existingIds.get(point.getNetWorthDate());
            if (id == null) {
                continue;
            }
            FundNetWorth update = new FundNetWorth();
            update.setId(id);
            update.setAccumulatedNetWorth(point.getAccumulatedNetWorth());
            fundNetWorthMapper.update(update);
            backfilled++;
        }
        if (backfilled > 0) {
            log.info("累计净值回填完成 code={} 补 {} 条", fundCode, backfilled);
        }
    }

    /**
     * 判断该基金是否还有缺累计净值的行，没有就跳过回填
     *
     * @param fundCode 6 位基金代码
     * @return 存在缺口返回 true
     */
    private boolean hasAccumulatedGap(String fundCode) {
        return fundNetWorthMapper.selectCountByQuery(QueryWrapper.create()
                .eq(FundNetWorth::getFundCode, fundCode)
                .isNull(FundNetWorth::getAccumulatedNetWorth)) > 0;
    }

    /**
     * 按区间查询净值，条件为空不参与过滤
     *
     * @param fundCode 6 位基金代码
     * @param start    起始日期，可为 null
     * @param end      结束日期，可为 null
     * @return 净值序列
     */
    @Override
    public List<FundNetWorthPoint> listRange(String fundCode, LocalDate start, LocalDate end) {
        List<FundNetWorth> rows = fundNetWorthMapper.selectListByQuery(QueryWrapper.create()
                .eq(FundNetWorth::getFundCode, fundCode)
                .ge(FundNetWorth::getNetWorthDate, start, start != null)
                .le(FundNetWorth::getNetWorthDate, end, end != null)
                .orderBy(FundNetWorth::getNetWorthDate, true));
        return rows.stream()
                .map(row -> new FundNetWorthPoint(row.getNetWorthDate(), row.getUnitNetWorth(),
                        row.getEquityReturn(), row.getAccumulatedNetWorth()))
                .toList();
    }

    /**
     * 取库内最大净值日期，走唯一索引的倒序扫描
     *
     * @param fundCode 6 位基金代码
     * @return 最新日期，无数据返回 null
     */
    @Override
    public LocalDate latestDate(String fundCode) {
        FundNetWorth latest = fundNetWorthMapper.selectOneByQuery(QueryWrapper.create()
                .select(FundNetWorth::getNetWorthDate)
                .eq(FundNetWorth::getFundCode, fundCode)
                .orderBy(FundNetWorth::getNetWorthDate, false)
                .limit(1));
        return latest == null ? null : latest.getNetWorthDate();
    }

    /**
     * 挑出库内尚不存在的净值点，已存在的日期按原值保留
     *
     * @param fundCode 6 位基金代码
     * @param points   净值序列
     * @return 待插入行
     */
    private List<FundNetWorth> collectNewRows(String fundCode, List<FundNetWorthPoint> points) {
        LocalDate latest = latestDate(fundCode);
        return points.stream()
                .filter(point -> latest == null || point.getNetWorthDate().isAfter(latest))
                .map(point -> toEntity(fundCode, point))
                .toList();
    }

    /**
     * 分批插入，单批 1000 行避免超过驱动与库的包体上限
     *
     * @param rows 待插入行
     */
    private void insertBatches(List<FundNetWorth> rows) {
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, rows.size());
            fundNetWorthMapper.insertBatchSelective(rows.subList(from, to));
        }
    }

    /**
     * 净值点转实体
     *
     * @param fundCode 6 位基金代码
     * @param point    净值点
     * @return 实体
     */
    private FundNetWorth toEntity(String fundCode, FundNetWorthPoint point) {
        FundNetWorth entity = new FundNetWorth();
        entity.setFundCode(fundCode);
        entity.setNetWorthDate(point.getNetWorthDate());
        entity.setUnitNetWorth(point.getUnitNetWorth());
        entity.setEquityReturn(point.getEquityReturn());
        entity.setAccumulatedNetWorth(point.getAccumulatedNetWorth());
        return entity;
    }
}
