package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.adapter.csindex.CsIndexIndicatorAdapter;
import com.lilac.adapter.model.IndexDividendPoint;
import com.lilac.adapter.model.IndexDividendSnapshot;
import com.lilac.domain.dto.dividend.DividendManualRequest;
import com.lilac.domain.dto.dividend.DividendQueryRequest;
import com.lilac.domain.entity.IndexDividend;
import com.lilac.domain.entity.Variety;
import com.lilac.domain.vo.IndexDividendOverviewVO;
import com.lilac.domain.vo.IndexDividendRefreshVO;
import com.lilac.domain.vo.IndexDividendVO;
import com.lilac.enums.DividendSource;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.IndexDividendMapper;
import com.lilac.mapper.VarietyMapper;
import com.lilac.service.IndexDividendService;
import com.lilac.utils.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 指数股息率业务实现，自动抓取与手工覆盖共用一张表，手动值优先
 */
@Slf4j
@Service
public class IndexDividendServiceImpl implements IndexDividendService {

    private static final Pattern INDEX_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]{6}$");
    private static final int DEFAULT_HISTORY_LIMIT = 20;
    private static final BigDecimal MIN_YIELD = BigDecimal.ZERO;
    private static final BigDecimal MAX_YIELD = new BigDecimal("100");

    @Resource
    private IndexDividendMapper indexDividendMapper;

    @Resource
    private VarietyMapper varietyMapper;

    @Resource
    private CsIndexIndicatorAdapter csIndexIndicatorAdapter;

    @Value("${fund.adapter.csindex.stale-days:7}")
    private int staleDays;

    /**
     * 刷新指定指数的股息率，留空时按库内红利持仓的跟踪指数推导
     *
     * @param indexCodes 指数代码列表，为空表示按红利持仓推导
     * @return 刷新结果
     */
    @Override
    public IndexDividendRefreshVO refresh(List<String> indexCodes) {
        List<String> targets = resolveTargets(indexCodes);
        ThrowUtils.throwIf(targets.isEmpty(), HttpsCodeEnum.NOT_FOUND_ERROR,
                "没有待刷新的指数，请先在品种里确认红利基金的跟踪指数或显式指定指数代码");

        IndexDividendRefreshVO result = new IndexDividendRefreshVO();
        result.setRefreshDate(LocalDate.now());
        List<IndexDividendRefreshVO.Item> items = new ArrayList<>(targets.size());
        for (String indexCode : targets) {
            items.add(refreshOne(indexCode));
        }
        result.setItems(items);
        result.setSavedCount((int) items.stream().filter(item -> Boolean.TRUE.equals(item.getSaved())).count());
        result.setManualRequiredCount((int) items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getManualRequired())).count());
        log.info("股息率刷新完成 目标 {} 个 写入 {} 个 需手工 {} 个",
                targets.size(), result.getSavedCount(), result.getManualRequiredCount());
        return result;
    }

    /**
     * 手工录入股息率，同一指数同一日期覆盖自动抓取结果
     *
     * @param request 手工录入请求
     * @return 落库后的总览视图
     */
    @Override
    public IndexDividendOverviewVO saveManual(DividendManualRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        String indexCode = request.getIndexCode() == null ? "" : request.getIndexCode().trim();
        ThrowUtils.throwIf(!INDEX_CODE_PATTERN.matcher(indexCode).matches(), HttpsCodeEnum.PARAMS_ERROR,
                "指数代码必须是 6 位字母数字，如 000922、H30269");
        ThrowUtils.throwIf(request.getDataDate() == null, HttpsCodeEnum.PARAMS_ERROR,
                "必须填写股息率的数据日期，否则无法判断是否过期");
        ThrowUtils.throwIf(request.getDataDate().isAfter(LocalDate.now()), HttpsCodeEnum.PARAMS_ERROR,
                "数据日期不能晚于今天");
        ThrowUtils.throwIf(request.getDividendYield() == null, HttpsCodeEnum.PARAMS_ERROR, "必须填写股息率");
        BigDecimal yield = request.getDividendYield();
        ThrowUtils.throwIf(yield.compareTo(MIN_YIELD) < 0 || yield.compareTo(MAX_YIELD) > 0,
                HttpsCodeEnum.PARAMS_ERROR, "股息率应在 0~100 的百分点区间内，如 4.08 表示 4.08%");

        IndexDividend row = new IndexDividend();
        row.setIndexCode(indexCode);
        row.setIndexName(request.getIndexName());
        row.setDataDate(request.getDataDate());
        row.setDividendYield(yield);
        row.setDividendYield1(yield);
        row.setDividendYield2(null);
        row.setSource(DividendSource.MANUAL);
        saveOrUpdate(row);
        log.info("手工覆盖股息率 code={} 日期={} 值={}", indexCode, row.getDataDate(), yield);
        return overviewOf(indexCode);
    }

    /**
     * 查询股息率，条件为空时返回全部已知指数各自的最新一期
     *
     * @param request 查询请求
     * @return 总览列表
     */
    @Override
    public List<IndexDividendOverviewVO> listOverview(DividendQueryRequest request) {
        DividendQueryRequest query = request == null ? new DividendQueryRequest() : request;
        String indexCode = resolveIndexCode(query);
        List<String> codes = indexCode != null ? List.of(indexCode) : allKnownIndexCodes();
        return codes.stream().map(this::overviewOf).filter(Objects::nonNull).toList();
    }

    /**
     * 刷新单只指数，取不到时明确给出需要手工覆盖
     *
     * @param indexCode 指数代码
     * @return 明细
     */
    private IndexDividendRefreshVO.Item refreshOne(String indexCode) {
        IndexDividendRefreshVO.Item item = new IndexDividendRefreshVO.Item();
        item.setIndexCode(indexCode);
        IndexDividendSnapshot snapshot;
        try {
            snapshot = csIndexIndicatorAdapter.fetch(indexCode);
        } catch (BusinessException e) {
            // 结构变化不等于没数据，如实报错但整批不中断，其余指数继续刷
            item.setSaved(Boolean.FALSE);
            item.setManualRequired(Boolean.TRUE);
            item.setMessage("自动抓取失败，请到手工覆盖入口录入：" + e.getMessage());
            log.warn("股息率抓取失败 code={}：{}", indexCode, e.getMessage());
            return item;
        }
        if (snapshot == null) {
            item.setSaved(Boolean.FALSE);
            item.setManualRequired(Boolean.TRUE);
            item.setMessage("中证指数官网未收录该指数（国证系列常见），请使用手工覆盖录入");
            return item;
        }
        int written = savePoints(snapshot);
        item.setIndexName(snapshot.getIndexName());
        item.setDataDate(snapshot.getDataDate());
        item.setDividendYield(snapshot.getDividendYield());
        item.setSaved(Boolean.TRUE);
        item.setManualRequired(Boolean.FALSE);
        item.setMessage("已写入 " + written + " 期，数据日期 " + snapshot.getDataDate());
        return item;
    }

    /**
     * 把抓取到的各期落库，手工录入过的同一日期不覆盖
     *
     * @param snapshot 抓取结果
     * @return 实际写入或更新的期数
     */
    private int savePoints(IndexDividendSnapshot snapshot) {
        int written = 0;
        for (IndexDividendPoint point : snapshot.getPoints()) {
            IndexDividend existing = findByIndexAndDate(point.getIndexCode(), point.getDataDate());
            // 手工值优先，自动抓取不得覆盖使用者的手工结论
            if (existing != null && existing.getSource() == DividendSource.MANUAL) {
                continue;
            }
            IndexDividend row = new IndexDividend();
            row.setIndexCode(point.getIndexCode());
            row.setIndexName(point.getIndexName());
            row.setDataDate(point.getDataDate());
            row.setDividendYield(point.usableYield());
            row.setDividendYield1(point.getDividendYield1());
            row.setDividendYield2(point.getDividendYield2());
            row.setSource(DividendSource.AUTO);
            saveOrUpdate(row);
            written++;
        }
        return written;
    }

    /**
     * 存在则更新、不存在则新增，避免撞「指数 + 数据日期」唯一索引
     *
     * @param row 待写入行
     */
    private void saveOrUpdate(IndexDividend row) {
        IndexDividend existing = findByIndexAndDate(row.getIndexCode(), row.getDataDate());
        if (existing == null) {
            indexDividendMapper.insertSelective(row);
            return;
        }
        row.setId(existing.getId());
        indexDividendMapper.update(row);
    }

    /**
     * 按指数与数据日期查一行
     *
     * @param indexCode 指数代码
     * @param dataDate  数据日期
     * @return 命中行，未命中返回 null
     */
    private IndexDividend findByIndexAndDate(String indexCode, LocalDate dataDate) {
        return indexDividendMapper.selectOneByQuery(QueryWrapper.create()
                .eq(IndexDividend::getIndexCode, indexCode)
                .eq(IndexDividend::getDataDate, dataDate));
    }

    /**
     * 组装单只指数的总览，含数据日期、新鲜度与关联基金
     *
     * @param indexCode 指数代码
     * @return 总览，无任何数据时返回 null
     */
    private IndexDividendOverviewVO overviewOf(String indexCode) {
        List<IndexDividend> rows = indexDividendMapper.selectListByQuery(QueryWrapper.create()
                .eq(IndexDividend::getIndexCode, indexCode)
                .orderBy(IndexDividend::getDataDate, false));
        if (rows.isEmpty()) {
            return null;
        }
        IndexDividend latest = rows.get(0);
        IndexDividendOverviewVO overview = new IndexDividendOverviewVO();
        overview.setIndexCode(indexCode);
        overview.setIndexName(latest.getIndexName());
        overview.setDataDate(latest.getDataDate());
        overview.setDividendYield(latest.getDividendYield());
        overview.setSourceLabel(latest.getSource() == null ? null : latest.getSource().getLabel());
        overview.setAutoAvailable(latest.getSource() == DividendSource.AUTO);
        overview.setFundCodes(fundCodesOf(indexCode));
        fillFreshness(overview, latest);
        overview.setHistory(rows.stream().limit(DEFAULT_HISTORY_LIMIT).map(this::toVO).toList());
        return overview;
    }

    /**
     * 计算距今天数与过期判定，过期时给出可读原因
     *
     * @param overview 总览
     * @param latest   最新一期
     */
    private void fillFreshness(IndexDividendOverviewVO overview, IndexDividend latest) {
        long ageDays = ChronoUnit.DAYS.between(latest.getDataDate(), LocalDate.now());
        overview.setAgeDays(ageDays);
        boolean stale = ageDays > staleDays;
        overview.setStale(stale);
        overview.setStaleReason(stale
                ? "数据日期 " + latest.getDataDate() + "，已过 " + ageDays + " 天，超过 " + staleDays + " 天阈值，不得据此给出加减仓动作"
                : null);
    }

    /**
     * 单期转视图
     *
     * @param row 股息率行
     * @return 视图
     */
    private IndexDividendVO toVO(IndexDividend row) {
        IndexDividendVO vo = BeanUtil.copyProperties(row, IndexDividendVO.class);
        vo.setSourceLabel(row.getSource() == null ? null : row.getSource().getLabel());
        return vo;
    }

    /**
     * 找出跟踪该指数的基金代码
     *
     * @param indexCode 指数代码
     * @return 基金代码列表
     */
    private List<String> fundCodesOf(String indexCode) {
        return varietyMapper.selectListByQuery(QueryWrapper.create()
                        .eq(Variety::getTrackIndexCode, indexCode))
                .stream()
                .map(Variety::getVarietyCode)
                .distinct()
                .toList();
    }

    /**
     * 库内出现过股息率数据或已被登记为跟踪指数的全部指数代码
     *
     * @return 指数代码列表
     */
    private List<String> allKnownIndexCodes() {
        Set<String> codes = new LinkedHashSet<>(distinctIndexCodes());
        varietyMapper.selectListByQuery(QueryWrapper.create()
                        .eq(Variety::getCategory, FundCategory.DIVIDEND))
                .stream()
                .map(Variety::getTrackIndexCode)
                .filter(code -> code != null && !code.isBlank())
                .forEach(codes::add);
        return List.copyOf(codes);
    }

    /**
     * 库内已有股息率数据的指数代码
     *
     * @return 指数代码列表
     */
    private List<String> distinctIndexCodes() {
        return indexDividendMapper.selectListByQuery(QueryWrapper.create()
                        .orderBy(IndexDividend::getIndexCode, true))
                .stream()
                .map(IndexDividend::getIndexCode)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * 刷新目标：显式指定优先，否则按库内红利持仓的跟踪指数推导
     *
     * @param indexCodes 显式指定
     * @return 去重后的指数代码
     */
    private List<String> resolveTargets(List<String> indexCodes) {
        if (indexCodes != null && !indexCodes.isEmpty()) {
            return indexCodes.stream()
                    .map(code -> code == null ? "" : code.trim())
                    .filter(code -> INDEX_CODE_PATTERN.matcher(code).matches())
                    .distinct()
                    .toList();
        }
        return varietyMapper.selectListByQuery(QueryWrapper.create()
                        .eq(Variety::getCategory, FundCategory.DIVIDEND))
                .stream()
                .map(Variety::getTrackIndexCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
    }

    /**
     * 按请求解析出待查指数，品种业务主键优先
     *
     * @param query 查询请求
     * @return 指数代码，未指定返回 null
     */
    private String resolveIndexCode(DividendQueryRequest query) {
        if (query.getIndexCode() != null && !query.getIndexCode().isBlank()) {
            return query.getIndexCode().trim();
        }
        if (query.getVarietyKey() == null || query.getVarietyKey().isBlank()) {
            return null;
        }
        Variety variety = varietyMapper.selectOneByQuery(QueryWrapper.create()
                .eq(Variety::getVarietyKey, query.getVarietyKey().trim()));
        ThrowUtils.throwIf(variety == null, HttpsCodeEnum.NOT_FOUND_ERROR, "未找到该品种");
        ThrowUtils.throwIf(variety.getTrackIndexCode() == null || variety.getTrackIndexCode().isBlank(),
                HttpsCodeEnum.NOT_FOUND_ERROR, "该品种未登记跟踪指数，无法取股息率");
        return variety.getTrackIndexCode();
    }
}
