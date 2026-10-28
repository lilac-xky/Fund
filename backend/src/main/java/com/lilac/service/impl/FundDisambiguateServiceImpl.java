package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.adapter.eastmoney.ExchangeIdentityAdapter;
import com.lilac.adapter.fund.DividendIndexRegistry;
import com.lilac.adapter.fund.FundCategoryResolver;
import com.lilac.adapter.fund.FundDetailAdapter;
import com.lilac.adapter.model.ExchangeIdentity;
import com.lilac.adapter.model.ExchangeKind;
import com.lilac.domain.dto.fund.FundConfirmRequest;
import com.lilac.domain.dto.fund.FundDisambiguateRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.entity.Variety;
import com.lilac.domain.vo.FundCandidateVO;
import com.lilac.domain.vo.FundCatalogStatusVO;
import com.lilac.domain.vo.FundCatalogSyncVO;
import com.lilac.domain.vo.FundDisambiguateVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.VarietyType;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.VarietyMapper;
import com.lilac.service.FundCatalogService;
import com.lilac.service.FundDisambiguateService;
import com.lilac.service.HoldingService;
import com.lilac.utils.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 基金代码消歧业务实现，按号段、全集查表、身份回显三级判定
 */
@Slf4j
@Service
public class FundDisambiguateServiceImpl implements FundDisambiguateService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final Pattern BACKEND_SUFFIX = Pattern.compile("[(（]后端[)）]");
    private static final Pattern SHARE_CLASS_SUFFIX = Pattern.compile("[A-Z]$");
    private static final int SHANGHAI_SEGMENT_MIN = 50;
    private static final int SHANGHAI_SEGMENT_MAX = 59;
    private static final int SHENZHEN_SEGMENT_MIN = 15;
    private static final int SHENZHEN_SEGMENT_MAX = 19;
    private static final int RELATED_LIMIT = 8;

    @Resource
    private FundCatalogService fundCatalogService;

    @Resource
    private ExchangeIdentityAdapter exchangeIdentityAdapter;

    @Resource
    private FundDetailAdapter fundDetailAdapter;

    @Resource
    private FundCategoryResolver fundCategoryResolver;

    @Resource
    private VarietyMapper varietyMapper;

    @Resource
    private HoldingService holdingService;

    /**
     * 按 6 位代码返回候选列表与身份回显
     *
     * @param request 消歧请求
     * @return 候选列表与回显信息
     */
    @Override
    public FundDisambiguateVO disambiguate(FundDisambiguateRequest request) {
        ThrowUtils.throwIf(request == null || request.getCode() == null, HttpsCodeEnum.PARAMS_ERROR);
        String code = request.getCode().trim();
        ThrowUtils.throwIf(!CODE_PATTERN.matcher(code).matches(), HttpsCodeEnum.PARAMS_ERROR, "代码必须是 6 位数字");

        // 全集过期时先同步，同步失败沿用旧缓存
        fundCatalogService.syncIfStale();
        FundCatalogStatusVO status = fundCatalogService.status();
        FundCatalog catalog = fundCatalogService.findByCode(code);

        List<ExchangeIdentity> identities;
        boolean degraded = false;
        try {
            identities = exchangeIdentityAdapter.resolveAll(code);
        } catch (BusinessException e) {
            // 行情不可用不影响全集消歧，标记降级即可
            log.warn("身份回显失败，按全集结果降级返回 code={}：{}", code, e.getMessage());
            identities = List.of();
            degraded = true;
        }
        ExchangeIdentity etf = pickIdentity(identities, ExchangeKind.ETF);
        ExchangeIdentity lof = pickIdentity(identities, ExchangeKind.LOF);
        ExchangeIdentity index = pickIdentity(identities, ExchangeKind.INDEX);
        ExchangeIdentity stock = pickIdentity(identities, ExchangeKind.STOCK);
        ExchangeIdentity exchangeIdentity = etf != null ? etf : lof;

        List<FundCandidateVO> candidates = new ArrayList<>(3);
        if (catalog != null) {
            candidates.add(buildFromCatalog(code, catalog, exchangeIdentity, identities));
        } else if (etf != null) {
            // 全集滞后于新发 ETF 时用行情回显兜住，否则新代码会被误判为不存在
            candidates.add(buildFromIdentity(code, etf));
        }
        if (index != null) {
            candidates.add(buildIndex(code, index));
        }
        ThrowUtils.throwIf(candidates.isEmpty(), HttpsCodeEnum.NOT_FOUND_ERROR,
                "未找到代码 " + code + " 对应的基金或指数");
        candidates.sort((left, right) -> Integer.compare(order(left.getVarietyType()), order(right.getVarietyType())));

        FundDisambiguateVO result = new FundDisambiguateVO();
        result.setCode(code);
        result.setExists(Boolean.TRUE);
        result.setCandidates(candidates);
        result.setDegraded(degraded);
        result.setCatalogUpdatedAt(status.getSyncTime());
        result.setStockHint(stock == null ? null : stock.name());
        result.setRelatedShareClasses(findRelatedShareClasses(code, catalog));
        result.setNeedConfirm(candidates.stream().anyMatch(candidate -> Boolean.TRUE.equals(candidate.getRecordable())
                && Boolean.FALSE.equals(candidate.getConfirmed())));
        fillRegistered(candidates);
        fillRegistered(result.getRelatedShareClasses());
        log.info("消歧完成 code={} 候选 {} 个 降级 {} 号段 {} 同码股票 {}",
                code, candidates.size(), degraded, segmentHint(code), result.getStockHint());
        return result;
    }

    /**
     * 确认候选并写入品种注册表，类型待确认时必须显式指定
     *
     * @param request 确认请求
     * @return 已写入的品种
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public VarietyVO confirm(FundConfirmRequest request) {
        ThrowUtils.throwIf(request == null || request.getCode() == null, HttpsCodeEnum.PARAMS_ERROR);
        String code = request.getCode().trim();
        ThrowUtils.throwIf(!CODE_PATTERN.matcher(code).matches(), HttpsCodeEnum.PARAMS_ERROR, "代码必须是 6 位数字");
        ThrowUtils.throwIf(request.getVarietyType() == null, HttpsCodeEnum.PARAMS_ERROR, "必须指定品种类型");
        ThrowUtils.throwIf(request.getVarietyType() == VarietyType.IDX, HttpsCodeEnum.PARAMS_ERROR,
                "指数不从持仓入口录入");

        FundCatalog catalog = fundCatalogService.findByCode(code);
        ThrowUtils.throwIf(catalog == null, HttpsCodeEnum.NOT_FOUND_ERROR,
                "未找到代码 " + code + " 对应的基金，请先刷新基金全集");

        boolean exchangeTraded = request.getVarietyType() == VarietyType.ETF;
        FundCategory inferred = fundCategoryResolver.resolve(catalog.getFundType(), catalog.getFundName(), exchangeTraded);
        FundCategory category = request.getCategory() == null ? inferred : request.getCategory();
        // 自动判定不出类型时不给默认值，避免把黄金当普通股票型静默使用
        ThrowUtils.throwIf(category == FundCategory.UNKNOWN, HttpsCodeEnum.PARAMS_ERROR, "该基金类型无法自动判定，请手动指定");
        String trackIndexCode = resolveTrackIndexCode(code, category, request.getTrackIndexCode());

        VarietySaveRequest saveRequest = new VarietySaveRequest();
        saveRequest.setVarietyKey(request.getVarietyType().name() + ":" + code);
        saveRequest.setVarietyType(request.getVarietyType());
        saveRequest.setVarietyCode(code);
        saveRequest.setVarietyName(catalog.getFundName());
        saveRequest.setCategory(category);
        saveRequest.setTrackIndexCode(trackIndexCode);
        saveRequest.setConfirmed(Boolean.TRUE);
        saveVariety(saveRequest);

        Variety saved = varietyMapper.selectOneByQuery(QueryWrapper.create()
                .eq(Variety::getVarietyKey, saveRequest.getVarietyKey()));
        return BeanUtil.copyProperties(saved, VarietyVO.class);
    }

    /**
     * 强制刷新基金全集缓存
     *
     * @return 同步结果
     */
    @Override
    public FundCatalogSyncVO syncCatalog() {
        return fundCatalogService.sync();
    }

    /**
     * 查询基金全集缓存状态
     *
     * @return 缓存状态
     */
    @Override
    public FundCatalogStatusVO catalogStatus() {
        return fundCatalogService.status();
    }

    /**
     * 按业务主键存在则更新、不存在则新增，避免重复确认同一标的撞唯一索引
     *
     * @param request 品种保存请求
     */
    private void saveVariety(VarietySaveRequest request) {
        Variety existing = varietyMapper.selectOneByQuery(QueryWrapper.create()
                .eq(Variety::getVarietyKey, request.getVarietyKey()));
        Variety entity = BeanUtil.copyProperties(request, Variety.class);
        if (existing == null) {
            varietyMapper.insertSelective(entity);
            return;
        }
        entity.setId(existing.getId());
        varietyMapper.update(entity);
    }

    /**
     * 按全集与身份回显拼出可录入候选
     *
     * @param code       6 位代码
     * @param catalog    全集行
     * @param identity   场内身份回显，可为 null
     * @param identities 该代码的全部行情身份
     * @return 候选项
     */
    private FundCandidateVO buildFromCatalog(String code, FundCatalog catalog, ExchangeIdentity identity,
                                             List<ExchangeIdentity> identities) {
        boolean exchangeTraded = isExchangeTraded(code, catalog, identity);
        FundCategory category = fundCategoryResolver.resolve(catalog.getFundType(), catalog.getFundName(), exchangeTraded);
        VarietyType varietyType = exchangeTraded ? VarietyType.ETF : VarietyType.OF;
        FundCandidateVO candidate = baseCandidate(code, varietyType, catalog.getFundName(), category);
        candidate.setConfirmed(category != FundCategory.UNKNOWN);
        candidate.setRecordable(Boolean.TRUE);
        candidate.setNote(buildNote(catalog, identity, identities));
        if (category == FundCategory.DIVIDEND) {
            candidate.setTrackIndexCode(resolveDividendIndex(code));
        }
        return candidate;
    }

    /**
     * 拼身份回显说明，同时点出同码在不同市场的其他标的
     *
     * @param catalog    全集行
     * @param identity   场内身份回显，可为 null
     * @param identities 该代码的全部行情身份
     * @return 说明文字
     */
    private String buildNote(FundCatalog catalog, ExchangeIdentity identity, List<ExchangeIdentity> identities) {
        // 同名不同市场的身份不算歧义，只有名称不同才需要提示核对
        List<String> others = identities.stream()
                .map(ExchangeIdentity::name)
                .filter(name -> !name.equals(catalog.getFundName()))
                .distinct()
                .toList();
        String suffix = others.isEmpty() ? "" : "；同码另有 " + String.join("、", others);
        if (identity == null) {
            return "名称取自基金全集「" + catalog.getFundName() + "」" + suffix;
        }
        return "行情身份回显：「" + identity.name() + "」" + suffix;
    }

    /**
     * 全集未收录但行情确认是场内 ETF 时构造候选
     *
     * @param code     6 位代码
     * @param identity 身份回显
     * @return 候选项
     */
    private FundCandidateVO buildFromIdentity(String code, ExchangeIdentity identity) {
        FundCategory category = fundCategoryResolver.resolve(null, identity.name(), true);
        FundCandidateVO candidate = baseCandidate(code, VarietyType.ETF, identity.name(),
                category == FundCategory.UNKNOWN ? FundCategory.ETF : category);
        candidate.setConfirmed(Boolean.TRUE);
        candidate.setRecordable(Boolean.TRUE);
        candidate.setNote("基金全集未收录，名称取自行情回显");
        return candidate;
    }

    /**
     * 构造指数候选，指数不从持仓入口录入
     *
     * @param code     6 位代码
     * @param identity 身份回显
     * @return 候选项
     */
    private FundCandidateVO buildIndex(String code, ExchangeIdentity identity) {
        FundCandidateVO candidate = baseCandidate(code, VarietyType.IDX, identity.name(), FundCategory.UNKNOWN);
        candidate.setConfirmed(Boolean.TRUE);
        candidate.setRecordable(Boolean.FALSE);
        candidate.setNote("指数，不从持仓入口录入");
        return candidate;
    }

    /**
     * 组装候选公共字段
     *
     * @param code        6 位代码
     * @param varietyType 品种类型
     * @param name        名称
     * @param category    基金类型
     * @return 候选项
     */
    private FundCandidateVO baseCandidate(String code, VarietyType varietyType, String name, FundCategory category) {
        FundCandidateVO candidate = new FundCandidateVO();
        candidate.setVarietyKey(varietyType.name() + ":" + code);
        candidate.setVarietyType(varietyType);
        candidate.setVarietyTypeLabel(varietyTypeLabel(varietyType));
        candidate.setVarietyCode(code);
        candidate.setVarietyName(name);
        candidate.setCategory(category);
        candidate.setCategoryLabel(category == null ? FundCategory.UNKNOWN.getLabel() : category.getLabel());
        return candidate;
    }

    /**
     * 判断是否场内交易，以行情身份为准，行情不可用时退化为号段加名称
     *
     * @param code     6 位代码
     * @param catalog  全集行
     * @param identity 身份回显，可为 null
     * @return 场内返回 true
     */
    private boolean isExchangeTraded(String code, FundCatalog catalog, ExchangeIdentity identity) {
        if (identity != null) {
            return identity.kind() == ExchangeKind.ETF;
        }
        // ETF 联接与 LOF 也能走到 50~59/15~19 号段，必须靠名称排除
        boolean nameLookLikeEtf = catalog.getFundName().toUpperCase().contains("ETF")
                && !catalog.getFundName().contains("联接");
        return segmentHint(code) != null && nameLookLikeEtf;
    }

    /**
     * 按类型挑出首个身份
     *
     * @param identities 身份列表
     * @param kind       需要的类型
     * @return 命中时返回身份，否则返回 null
     */
    private ExchangeIdentity pickIdentity(List<ExchangeIdentity> identities, ExchangeKind kind) {
        return identities.stream().filter(identity -> identity.kind() == kind).findFirst().orElse(null);
    }

    /**
     * 号段提示，沪市 50~59 开头、深市 15~19 开头返回所在市场
     *
     * @param code 6 位代码
     * @return 市场标识，不在 ETF 号段返回 null
     */
    private String segmentHint(String code) {
        int prefix = Integer.parseInt(code.substring(0, 2));
        if (prefix >= SHANGHAI_SEGMENT_MIN && prefix <= SHANGHAI_SEGMENT_MAX) {
            return "沪市";
        }
        if (prefix >= SHENZHEN_SEGMENT_MIN && prefix <= SHENZHEN_SEGMENT_MAX) {
            return "深市";
        }
        return null;
    }

    /**
     * 名称相近的同系列份额，A/C 类并列展示供核对
     *
     * @param code    当前代码
     * @param catalog 当前全集行，可为 null
     * @return 相关份额列表
     */
    private List<FundCandidateVO> findRelatedShareClasses(String code, FundCatalog catalog) {
        if (catalog == null) {
            return List.of();
        }
        String baseName = baseName(catalog.getFundName());
        if (baseName.isBlank()) {
            return List.of();
        }
        List<FundCandidateVO> related = new ArrayList<>();
        for (FundCatalog row : fundCatalogService.searchByName(baseName, RELATED_LIMIT * 3)) {
            if (row.getFundCode().equals(code) || !baseName(row.getFundName()).equals(baseName)) {
                continue;
            }
            boolean exchangeTraded = segmentHint(row.getFundCode()) != null
                    && row.getFundName().toUpperCase().contains("ETF") && !row.getFundName().contains("联接");
            FundCategory category = fundCategoryResolver.resolve(row.getFundType(), row.getFundName(), exchangeTraded);
            FundCandidateVO candidate = baseCandidate(row.getFundCode(), exchangeTraded ? VarietyType.ETF : VarietyType.OF,
                    row.getFundName(), category);
            candidate.setConfirmed(category != FundCategory.UNKNOWN);
            candidate.setRecordable(Boolean.TRUE);
            candidate.setNote("名称相近的份额，请核对代码后再录入");
            related.add(candidate);
            if (related.size() >= RELATED_LIMIT) {
                break;
            }
        }
        return related;
    }

    /**
     * 去掉份额后缀得到系列名，用于判断名称是否相近
     *
     * @param fundName 基金简称
     * @return 系列名
     */
    private String baseName(String fundName) {
        String withoutBackend = BACKEND_SUFFIX.matcher(fundName).replaceAll("").trim();
        return SHARE_CLASS_SUFFIX.matcher(withoutBackend).replaceAll("").trim();
    }

    /**
     * 红利基金查跟踪指数，取不到返回 null 交给人工指定
     *
     * @param code 6 位代码
     * @return 指数代码
     */
    private String resolveDividendIndex(String code) {
        return fundDetailAdapter.fetchTrackIndexName(code)
                .map(DividendIndexRegistry::findByTrackIndexName)
                .orElse(null);
    }

    /**
     * 红利必须带跟踪指数，其余类型允许为空
     *
     * @param code           6 位代码
     * @param category       基金类型
     * @param submittedIndex 请求中提交的指数代码
     * @return 跟踪指数代码
     */
    private String resolveTrackIndexCode(String code, FundCategory category, String submittedIndex) {
        if (submittedIndex != null && !submittedIndex.isBlank()) {
            return submittedIndex.trim();
        }
        if (category != FundCategory.DIVIDEND) {
            return null;
        }
        String resolved = resolveDividendIndex(code);
        ThrowUtils.throwIf(resolved == null, HttpsCodeEnum.PARAMS_ERROR, "红利基金必须指定跟踪指数，才能取到股息率");
        return resolved;
    }

    /**
     * 候选排序，场外基金在前、场内 ETF 居中、指数最后
     *
     * @param varietyType 品种类型
     * @return 排序序号
     */
    private int order(VarietyType varietyType) {
        return switch (varietyType) {
            case OF -> 0;
            case ETF -> 1;
            case IDX -> 2;
        };
    }

    /**
     * 品种类型中文名
     *
     * @param varietyType 品种类型
     * @return 中文名
     */
    private String varietyTypeLabel(VarietyType varietyType) {
        return switch (varietyType) {
            case OF -> "场外基金";
            case ETF -> "场内 ETF";
            case IDX -> "指数";
        };
    }

    /**
     * 回填候选的注册状态与持有概况，已确认过的代码不再重复询问
     *
     * @param candidates 候选列表
     */
    private void fillRegistered(List<FundCandidateVO> candidates) {
        if (candidates == null) {
            return;
        }
        for (FundCandidateVO candidate : candidates) {
            if (!Boolean.TRUE.equals(candidate.getRecordable()) || candidate.getVarietyKey() == null) {
                continue;
            }
            Variety variety = varietyMapper.selectOneByQuery(QueryWrapper.create()
                    .eq(Variety::getVarietyKey, candidate.getVarietyKey()));
            candidate.setRegistered(variety != null);
            candidate.setHoldingSummary(variety == null ? null : holdingService.summaryOf(candidate.getVarietyKey()));
        }
    }
}
