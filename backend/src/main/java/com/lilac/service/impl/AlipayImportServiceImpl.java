package com.lilac.service.impl;

import com.lilac.adapter.alipay.AlipayBillParser;
import com.lilac.adapter.alipay.AlipayBillRow;
import com.lilac.adapter.alipay.AlipayFundClassifier;
import com.lilac.adapter.alipay.AlipayFundOrder;
import com.lilac.adapter.alipay.AlipayImportRow;
import com.lilac.adapter.alipay.FundNameMatcher;
import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.entity.HoldingChange;
import com.lilac.domain.entity.Variety;
import com.lilac.enums.AlipayAction;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.HoldingChangeMapper;
import com.lilac.mapper.VarietyMapper;
import com.lilac.service.AlipayImportService;
import com.lilac.service.FundCatalogService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 支付宝账单导入实现，解析后逐行匹配基金并去重，全程只读不写持仓
 */
@Slf4j
@Service
public class AlipayImportServiceImpl implements AlipayImportService {

    /**
     * 名称模糊匹配的候选上限，太多说明关键词太短
     */
    private static final int CANDIDATE_LIMIT = 8;

    /**
     * 同名候选数超过它就判为歧义，交人工选
     */
    private static final int AMBIGUOUS_THRESHOLD = 1;

    /**
     * 名称相似度下限，低于它不认为同一只基金；0.72 能容下「发起」「人民币」「波动→波」这类差异
     */
    private static final double SIMILAR_THRESHOLD = 0.72;

    /**
     * 字符覆盖率下限，用来兜住「南方红利低波」与「南方标普红利低波」这种中间插字的叫法
     */
    private static final double COVERAGE_THRESHOLD = 0.85;

    @Resource
    private AlipayBillParser alipayBillParser;

    @Resource
    private AlipayFundClassifier alipayFundClassifier;

    @Resource
    private FundNameMatcher fundNameMatcher;

    @Resource
    private FundCatalogService fundCatalogService;

    @Resource
    private VarietyMapper varietyMapper;

    @Resource
    private HoldingChangeMapper holdingChangeMapper;

    /**
     * 解析账单并生成待确认清单，重复的交易号与退款行在这里被消掉
     *
     * @param bytes    账单文件字节
     * @param fileName 原始文件名，仅用于回显
     * @return 待确认行
     */
    @Override
    public List<AlipayImportRow> preview(byte[] bytes, String fileName) {
        List<AlipayBillRow> rows = alipayBillParser.parse(bytes);
        if (rows.isEmpty()) {
            throw new BusinessException(HttpsCodeEnum.PARAMS_ERROR,
                    "账单里没有可识别的交易记录，确认导出的是「交易记录明细」而不是账单汇总");
        }
        List<AlipayFundOrder> orders = alipayFundClassifier.classify(rows);
        List<AlipayFundOrder> merged = dropRefunded(orders);
        // 库里已有的交易号，用来把「上次已经导过」的行直接标出来，不必等人工再点清理
        Set<String> imported = importedTradeNos();
        List<AlipayImportRow> result = new ArrayList<>();
        int seq = 0;
        for (AlipayFundOrder order : merged) {
            AlipayImportRow row = toRow(++seq, order);
            if (imported.contains(order.getTradeNo())) {
                row.setStatus("已导入");
                row.setReason("这笔交易号在库里已存在，上次已经导入过，跳过即可");
                row.setSelectable(Boolean.FALSE);
            }
            result.add(row);
        }
        log.info("账单 {} 解析出 {} 条记录，去重抵消后剩 {} 条待确认", fileName, rows.size(),
                result.size());
        return result;
    }

    /**
     * 用同交易号的退款行冲销原动作，并去掉完全重复的行
     *
     * @param orders 识别结果
     * @return 抵消后的结果
     */
    private List<AlipayFundOrder> dropRefunded(List<AlipayFundOrder> orders) {
        // 按交易号归组：同组的「买入 + 买入退款」（含转换找零）整组丢弃，金额即已扣差异的净额
        Map<String, List<AlipayFundOrder>> byTradeNo = new LinkedHashMap<>();
        for (AlipayFundOrder order : orders) {
            byTradeNo.computeIfAbsent(order.getTradeNo(), key -> new ArrayList<>()).add(order);
        }
        List<AlipayFundOrder> kept = new ArrayList<>();
        for (List<AlipayFundOrder> group : byTradeNo.values()) {
            boolean refunded = group.stream()
                    .anyMatch(item -> item.getAction() == AlipayAction.REFUND);
            if (refunded) {
                // 有退款就把整组拿掉：退了钱再记买入会让份额虚高
                continue;
            }
            // 同组内可能还有同名重复（导出重叠），按「动作 + 金额」再去一层
            Map<String, AlipayFundOrder> unique = new LinkedHashMap<>();
            for (AlipayFundOrder order : group) {
                String key = order.getAction() + "|" + order.getAmount() + "|"
                        + order.getFundName();
                unique.putIfAbsent(key, order);
            }
            kept.addAll(unique.values());
        }
        // 交易号为空的行无法归组，原样保留交人工判断
        List<AlipayFundOrder> rest = new ArrayList<>();
        for (AlipayFundOrder order : orders) {
            if (order.getTradeNo() == null || order.getTradeNo().isBlank()) {
                rest.add(order);
            }
        }
        kept.sort((left, right) -> {
            if (left.getOrderTime() == null || right.getOrderTime() == null) {
                return 0;
            }
            return right.getOrderTime().compareTo(left.getOrderTime());
        });
        return kept;
    }
    /**
     * 把一条动作转成待确认行，含基金代码匹配结果
     *
     * @param seq   行内序号
     * @param order 动作
     * @return 待确认行
     */
    private AlipayImportRow toRow(int seq, AlipayFundOrder order) {
        AlipayImportRow row = new AlipayImportRow();
        row.setSeq(seq);
        row.setTradeNo(order.getTradeNo());
        row.setAction(order.getAction().name());
        row.setActionLabel(order.getAction().getLabel());
        row.setFundName(order.getFundName());
        row.setTargetFundName(order.getTargetFundName());
        row.setAmount(order.getAmount());
        row.setOrderTime(order.getOrderTime());
        row.setConfirmDate(order.getConfirmDate());
        List<AlipayImportRow.Candidate> candidates = candidatesOf(order.getFundName());
        row.setCandidates(candidates);
        fillMatch(row, candidates);
        if (order.getAction() == AlipayAction.CONVERT) {
            List<AlipayImportRow.Candidate> targets = candidatesOf(order.getTargetFundName());
            row.setTargetCandidates(targets);
        }
        row.setStatus(statusOf(row, order));
        row.setReason(reasonOf(row, order, candidates));
        row.setSelectable(selectableOf(row));
        return row;
    }

    /**
     * 按名称找候选：先精确、再在已注册品种里按相似度找、最后才到基金全集里模糊查
     *
     * @param fundName 基金名称
     * @return 候选列表
     */
    private List<AlipayImportRow.Candidate> candidatesOf(String fundName) {
        List<AlipayImportRow.Candidate> candidates = new ArrayList<>();
        if (fundName == null || fundName.isBlank()) {
            return candidates;
        }
        String target = fundNameMatcher.normalize(fundName);
        // 第一优先：已注册品种里名称归一化后完全相等，这是使用者实际持有的那只
        List<AlipayImportRow.Candidate> registeredHits = matchRegistered(target, 1.0);
        if (!registeredHits.isEmpty()) {
            return registeredHits;
        }
        // 第二优先：已注册品种里名称高度相似（蚂蚁叫全称、天天叫简称）
        List<AlipayImportRow.Candidate> similarHits = matchRegistered(target, SIMILAR_THRESHOLD);
        if (!similarHits.isEmpty()) {
            return similarHits;
        }
        // 第三优先：基金全集里的模糊查，仍然只认归一化后相等或高度相似
        List<AlipayImportRow.Candidate> catalogHits = matchCatalog(target);
        if (!catalogHits.isEmpty()) {
            return catalogHits;
        }
        // 都没命中时退回计数最高的几条，供人工选择而不是直接判「查不到」
        List<FundCatalog> loose = fundCatalogService.searchByName(fundName, CANDIDATE_LIMIT);
        Map<String, Variety> registered = registeredVarieties();
        for (FundCatalog catalog : loose) {
            candidates.add(toCandidate(catalog, registered.get(catalog.getFundCode())));
        }
        return candidates;
    }

    /**
     * 在已注册品种里按相似度找，已注册的优先是因为它就是使用者持有的那只
     *
     * @param target    归一化后的目标名称
     * @param threshold 相似度下限，1.0 表示完全相等
     * @return 候选列表
     */
    private List<AlipayImportRow.Candidate> matchRegistered(String target, double threshold) {
        List<AlipayImportRow.Candidate> hits = new ArrayList<>();
        for (Variety variety : varietyMapper.selectListByQuery(QueryWrapper.create())) {
            if (variety.getVarietyCode() == null || variety.getVarietyName() == null) {
                continue;
            }
            String normalized = fundNameMatcher.normalize(variety.getVarietyName());
            // A 类与 C 类是不同份额，名称再像也不能混
            if (!fundNameMatcher.classCompatible(target, normalized)) {
                continue;
            }
            double score = fundNameMatcher.similarity(target, normalized);
            if (score < threshold) {
                continue;
            }
            AlipayImportRow.Candidate candidate = new AlipayImportRow.Candidate();
            candidate.setFundCode(variety.getVarietyCode());
            candidate.setFundName(variety.getVarietyName());
            candidate.setRegistered(Boolean.TRUE);
            candidate.setVarietyKey(variety.getVarietyKey());
            candidate.setSimilarity(round(score));
            hits.add(candidate);
        }
        // 相似度高的排前面，同分时按代码升序保证结果稳定
        hits.sort(Comparator.comparing(AlipayImportRow.Candidate::getSimilarity).reversed()
                .thenComparing(AlipayImportRow.Candidate::getFundCode));
        return hits;
    }

    /**
     * 到基金全集里按相似度找，命中不到时才由调用方退回模糊查
     *
     * @param target 归一化后的目标名称
     * @return 候选列表
     */
    private List<AlipayImportRow.Candidate> matchCatalog(String target) {
        Map<String, Variety> registered = registeredVarieties();
        List<AlipayImportRow.Candidate> hits = new ArrayList<>();
        for (FundCatalog catalog : catalogCandidates(target)) {
            if (catalog.getFundName() == null) {
                continue;
            }
            String normalized = fundNameMatcher.normalize(catalog.getFundName());
            if (!fundNameMatcher.classCompatible(target, normalized)) {
                continue;
            }
            double score = fundNameMatcher.similarity(target, normalized);
            if (score < SIMILAR_THRESHOLD) {
                continue;
            }
            AlipayImportRow.Candidate candidate = toCandidate(catalog,
                    registered.get(catalog.getFundCode()));
            candidate.setSimilarity(round(score));
            hits.add(candidate);
        }
        hits.sort(Comparator.comparing(AlipayImportRow.Candidate::getSimilarity).reversed()
                .thenComparing(AlipayImportRow.Candidate::getFundCode));
        return hits;
    }

    /**
     * 取参与比对的候选全集行
     *
     * <p>账单「南方红利低波50ETF联接C」与全集「南方标普红利低波50ETF联接C」中间多了「标普」，
     * SQL 的 LIKE 要求连续子串，这类名字查不出来，故先按关键词查一轮，再用字符覆盖率本地补一轮。</p>
     *
     * @param target 归一化后的目标名称
     * @return 候选全集行
     */
    private List<FundCatalog> catalogCandidates(String target) {
        List<FundCatalog> result = new ArrayList<>();
        for (FundCatalog catalog : fundCatalogService.listAllNames()) {
            if (catalog.getFundName() == null) {
                continue;
            }
            String normalized = fundNameMatcher.normalize(catalog.getFundName());
            if (fundNameMatcher.covers(target, normalized, COVERAGE_THRESHOLD)) {
                result.add(catalog);
            }
        }
        return result;
    }

    /**
     * 全集行转候选，顺带标出是否已注册
     *
     * @param catalog  全集行
     * @param variety  已注册品种，未注册为 null
     * @return 候选
     */
    private AlipayImportRow.Candidate toCandidate(FundCatalog catalog, Variety variety) {
        AlipayImportRow.Candidate candidate = new AlipayImportRow.Candidate();
        candidate.setFundCode(catalog.getFundCode());
        candidate.setFundName(catalog.getFundName());
        candidate.setFundType(catalog.getFundType());
        candidate.setRegistered(variety != null);
        candidate.setVarietyKey(variety == null ? null : variety.getVarietyKey());
        return candidate;
    }

    /**
     * 相似度保留两位小数，前端展示用
     *
     * @param score 原始相似度
     * @return 保留两位的值
     */
    private Double round(double score) {
        return Math.round(score * 100) / 100.0;
    }

    /**
     * 取库里已有变动记录上的交易号，用于跨次导入去重
     *
     * @return 交易号集合
     */
    private Set<String> importedTradeNos() {
        Set<String> set = new HashSet<>();
        for (HoldingChange change : holdingChangeMapper.selectListByQuery(QueryWrapper.create()
                .isNotNull(HoldingChange::getTradeNo))) {
            if (change.getTradeNo() != null && !change.getTradeNo().isBlank()) {
                set.add(change.getTradeNo());
            }
        }
        return set;
    }

    /**
     * 已注册品种按 6 位代码建索引，用于判断候选能否直接落库
     *
     * @return 代码到品种
     */
    private Map<String, Variety> registeredVarieties() {
        Map<String, Variety> map = new HashMap<>();
        for (Variety variety : varietyMapper.selectListByQuery(QueryWrapper.create())) {
            if (variety.getVarietyCode() != null) {
                map.putIfAbsent(variety.getVarietyCode(), variety);
            }
        }
        return map;
    }

    /**
     * 命中唯一候选时把代码与品种主键填进行，多个候选留给人工
     *
     * @param row        待确认行
     * @param candidates 候选
     */
    private void fillMatch(AlipayImportRow row, List<AlipayImportRow.Candidate> candidates) {
        if (candidates.size() != AMBIGUOUS_THRESHOLD) {
            return;
        }
        AlipayImportRow.Candidate only = candidates.get(0);
        row.setFundCode(only.getFundCode());
        row.setVarietyKey(only.getVarietyKey());
    }

    /**
     * 判定行状态：已注册且能落库的可导入，转换行必须人工补信息
     *
     * @param row   待确认行
     * @param order 动作
     * @return 状态
     */
    private String statusOf(AlipayImportRow row, AlipayFundOrder order) {
        if (order.getAction() == AlipayAction.UNKNOWN) {
            return "未识别";
        }
        if (order.getAction() == AlipayAction.CONVERT) {
            return "待补信息";
        }
        if (row.getCandidates().isEmpty()) {
            return "未匹配";
        }
        // 只有一个候选且已注册才可直接导入，其余都要人确认
        if (row.getCandidates().size() > AMBIGUOUS_THRESHOLD) {
            return "待选择";
        }
        return row.getVarietyKey() != null ? "可导入" : "待注册";
    }

    /**
     * 拼状态说明，把需要人工处理的原因写清楚
     *
     * @param row        待确认行
     * @param order      动作
     * @param candidates 候选
     * @return 说明
     */
    private String reasonOf(AlipayImportRow row, AlipayFundOrder order,
                            List<AlipayImportRow.Candidate> candidates) {
        if (order.getAction() == AlipayAction.UNKNOWN) {
            return "商品名称里没有识别到买入、卖出、分红或转换，请手工处理";
        }
        if (order.getAction() == AlipayAction.CONVERT) {
            return "转换只有金额，两边份额与净值不在账单里，需要在支付宝里点开这一笔补齐后提交";
        }
        if (candidates.isEmpty()) {
            return "基金全集里没查到「" + order.getFundName() + "」，请先同步基金全集或手工指定代码";
        }
        if (candidates.size() > AMBIGUOUS_THRESHOLD) {
            return "名称相似的有 " + candidates.size() + " 只，请选定具体那一个";
        }
        if (row.getVarietyKey() == null) {
            return "该基金还没注册，导入前需先在持仓页确认身份";
        }
        // 蚂蚁财富用全称、天天基金用简称，两边叫法不同时要说清是按什么对上的
        String matched = candidates.get(0).getFundName();
        if (matched != null && !matched.equals(order.getFundName())) {
            Double score = candidates.get(0).getSimilarity();
            return "按名称相似度 " + (score == null ? "1" : score) + " 对到「" + matched
                    + "」（账单写的是「" + order.getFundName() + "」），确认无误再导入";
        }
        if (order.getAction() == AlipayAction.CASH_DIVIDEND) {
            return "现金分红只留档核对，不计入持仓与收益，避免与系统自动推算的除息重复";
        }
        if (order.getAction() == AlipayAction.CONVERT_IN) {
            return "由「" + order.getNote() + "」；金额按确认日净值折算份额，与买入同口径";
        }
        return "确认日期 " + order.getConfirmDate() + "（按 15:00 口径换算）";
    }

    /**
     * 是否默认勾选：能直接落库的才默认选上，其余留给人处理
     *
     * @param row 待确认行
     * @return 可勾选返回 true
     */
    private Boolean selectableOf(AlipayImportRow row) {
        return "可导入".equals(row.getStatus()) && row.getAmount() != null;
    }
}
