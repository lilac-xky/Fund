package com.lilac.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lilac.adapter.fund.FundCategoryResolver;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.variety.CategoryBatchRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.entity.Variety;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.PendingCategoryVO;
import com.lilac.domain.vo.PendingItemVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.mapper.VarietyMapper;
import com.lilac.service.HoldingService;
import com.lilac.service.VarietyService;
import com.lilac.utils.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 品种注册表业务实现
 */
@Slf4j
@Service
public class VarietyServiceImpl implements VarietyService {

    @Resource
    private VarietyMapper varietyMapper;

    @Resource
    private FundCategoryResolver resolver;

    @Resource
    private HoldingService holdingService;

    /**
     * 统一查询品种
     *
     * @param request 查询请求
     * @return 品种视图列表
     */
    @Override
    public List<VarietyVO> listVariety(VarietyQueryRequest request) {
        VarietyQueryRequest query = request == null ? new VarietyQueryRequest() : request;
        List<Variety> rows = varietyMapper.selectListByQuery(QueryWrapper.create()
                .eq(Variety::getVarietyKey, query.getVarietyKey(), query.getVarietyKey() != null)
                .eq(Variety::getVarietyCode, query.getVarietyCode(), query.getVarietyCode() != null)
                .eq(Variety::getVarietyType, query.getVarietyType(), query.getVarietyType() != null)
                .eq(Variety::getCategory, query.getCategory(), query.getCategory() != null)
                .eq(Variety::getConfirmed, query.getConfirmed(), query.getConfirmed() != null)
                .orderBy(Variety::getVarietyType, true));
        return BeanUtil.copyToList(rows, VarietyVO.class);
    }

    /**
     * 按业务主键存在则更新、不存在则新增，避免重复录入同一标的撞唯一索引
     *
     * @param request 保存请求
     * @return 是否成功
     */
    @Override
    public boolean saveVariety(VarietySaveRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getVarietyKey() == null || request.getVarietyKey().isBlank(),
                HttpsCodeEnum.PARAMS_ERROR, "品种业务主键不能为空");
        Variety variety = BeanUtil.copyProperties(request, Variety.class);
        VarietyQueryRequest query = new VarietyQueryRequest();
        query.setVarietyKey(request.getVarietyKey());
        List<Variety> existing = varietyMapper.selectListByQuery(QueryWrapper.create()
                .eq(Variety::getVarietyKey, query.getVarietyKey()));
        if (existing.isEmpty()) {
            return varietyMapper.insertSelective(variety) > 0;
        }
        // 沿用已存在行的雪花主键，否则会写成新行并撞上业务唯一索引
        variety.setId(existing.get(0).getId());
        return varietyMapper.update(variety) > 0;
    }

    /**
     * 列出待确认类型的标的，只收 UNKNOWN 且未确认过的行
     *
     * @param holdingOnly 是否只看持有中的
     * @return 待确认清单
     */
    @Override
    public PendingCategoryVO listPending(boolean holdingOnly) {
        List<Variety> rows = varietyMapper.selectListByQuery(QueryWrapper.create()
                .eq(Variety::getCategory, FundCategory.UNKNOWN)
                .orderBy(Variety::getVarietyType, true));
        Set<String> holdingKeys = holdingOnly ? holdingKeys() : Set.of();
        List<PendingItemVO> items = new ArrayList<>();
        int suspected = 0;
        for (Variety row : rows) {
            boolean holding = holdingKeys.contains(row.getVarietyKey());
            if (holdingOnly && !holding) {
                continue;
            }
            PendingItemVO item = new PendingItemVO();
            item.setVarietyKey(row.getVarietyKey());
            item.setVarietyCode(row.getVarietyCode());
            item.setVarietyName(row.getVarietyName());
            item.setVarietyType(row.getVarietyType());
            item.setCategory(row.getCategory());
            item.setTrackIndexCode(row.getTrackIndexCode());
            item.setHints(resolver.hintsOf(row.getVarietyName()));
            item.setSuggested(suggestOf(row));
            item.setHolding(holding);
            item.setReason(reasonOf(row));
            if (item.getSuggested() != null) {
                suspected++;
            }
            items.add(item);
        }
        PendingCategoryVO vo = new PendingCategoryVO();
        vo.setItems(items);
        vo.setTotal(items.size());
        vo.setSuspectedCount(suspected);
        vo.setNotice(items.isEmpty()
                ? "没有待确认类型的标的"
                : (suspected > 0
                        ? "有 " + suspected + " 只可按名称关键词直接指定，其余需要你判断"
                        : "这些标的无法从名称推断类型，请逐个指定"));
        return vo;
    }

    /**
     * 批量指定类型与跟踪指数，指定后立即生效
     *
     * @param request 批量请求
     * @return 实际处理的只数
     */
    @Override
    public int assignCategory(CategoryBatchRequest request) {
        ThrowUtils.throwIf(request == null, HttpsCodeEnum.PARAMS_ERROR, "批量请求不能为空");
        ThrowUtils.throwIf(request.getVarietyKeys() == null || request.getVarietyKeys().isEmpty(),
                HttpsCodeEnum.PARAMS_ERROR, "请至少选择一个标的");
        ThrowUtils.throwIf(request.getCategory() == null || request.getCategory() == FundCategory.UNKNOWN,
                HttpsCodeEnum.PARAMS_ERROR, "必须指定一个确定的类型");
        if (request.getCategory() == FundCategory.DIVIDEND) {
            // 红利没有跟踪指数就取不到股息率，等于策略不工作，宁可拒绝
            ThrowUtils.throwIf(request.getTrackIndexCode() == null || request.getTrackIndexCode().isBlank(),
                    HttpsCodeEnum.PARAMS_ERROR, "红利类型必须同时指定跟踪指数代码，否则取不到股息率");
        }
        int updated = 0;
        for (String varietyKey : request.getVarietyKeys()) {
            if (varietyKey == null || varietyKey.isBlank()) {
                continue;
            }
            Variety existing = varietyMapper.selectOneByQuery(QueryWrapper.create()
                    .eq(Variety::getVarietyKey, varietyKey.trim()));
            if (existing == null) {
                log.warn("批量指定类型跳过不存在的品种 {}", varietyKey);
                continue;
            }
            Variety update = new Variety();
            update.setId(existing.getId());
            update.setCategory(request.getCategory());
            update.setConfirmed(Boolean.TRUE);
            if (request.getTrackIndexCode() != null && !request.getTrackIndexCode().isBlank()) {
                update.setTrackIndexCode(request.getTrackIndexCode().trim());
            }
            if (request.getDividendMethod() != null) {
                update.setDividendMethod(request.getDividendMethod());
            }
            if (varietyMapper.update(update) > 0) {
                updated++;
            }
        }
        log.info("批量指定类型完成 类型={} 处理 {} 只", request.getCategory(), updated);
        return updated;
    }

    /**
     * 按名称关键词给一个建议类型，猜不出来返回 null
     *
     * @param row 品种
     * @return 建议类型
     */
    private FundCategory suggestOf(Variety row) {
        String name = row.getVarietyName();
        if (resolver.isGoldEquity(name)) {
            return FundCategory.STOCK;
        }
        if (resolver.isGold(name)) {
            return FundCategory.GOLD;
        }
        if (name != null && (name.contains("红利") || name.contains("股息"))) {
            return FundCategory.DIVIDEND;
        }
        return null;
    }

    /**
     * 拼推断不出来的原因
     *
     * @param row 品种
     * @return 原因
     */
    private String reasonOf(Variety row) {
        String reason = resolver.pendingReasonOf(null, row.getVarietyName());
        if (reason != null) {
            return reason;
        }
        FundCategory suggested = suggestOf(row);
        if (suggested == FundCategory.DIVIDEND) {
            return "名称含红利关键词，请确认类型并填写跟踪指数代码";
        }
        return "上游类型与名称都不足以确定，请手工指定";
    }

    /**
     * 取持有中的品种主键集合
     *
     * @return 业务主键集合
     */
    private Set<String> holdingKeys() {
        Set<String> keys = new HashSet<>();
        for (HoldingVO holding : holdingService.listHolding(holdingRequest())) {
            if (holding.getVarietyKey() != null) {
                keys.add(holding.getVarietyKey());
            }
        }
        return keys;
    }

    /**
     * 构造持有中查询请求
     *
     * @return 查询请求
     */
    private HoldingQueryRequest holdingRequest() {
        HoldingQueryRequest query = new HoldingQueryRequest();
        query.setStatus(HoldingStatus.HOLDING);
        return query;
    }
}
