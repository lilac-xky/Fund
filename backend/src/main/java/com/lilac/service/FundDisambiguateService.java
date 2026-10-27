package com.lilac.service;

import com.lilac.domain.dto.fund.FundConfirmRequest;
import com.lilac.domain.dto.fund.FundDisambiguateRequest;
import com.lilac.domain.vo.FundCatalogStatusVO;
import com.lilac.domain.vo.FundCatalogSyncVO;
import com.lilac.domain.vo.FundDisambiguateVO;
import com.lilac.domain.vo.VarietyVO;

/**
 * 基金代码消歧业务接口
 */
public interface FundDisambiguateService {

    /**
     * 按 6 位代码返回候选列表与身份回显
     *
     * @param request 消歧请求
     * @return 候选列表与回显信息
     */
    FundDisambiguateVO disambiguate(FundDisambiguateRequest request);

    /**
     * 确认候选并写入品种注册表，类型待确认时需显式指定
     *
     * @param request 确认请求
     * @return 已写入的品种
     */
    VarietyVO confirm(FundConfirmRequest request);

    /**
     * 强制刷新基金全集缓存
     *
     * @return 同步结果
     */
    FundCatalogSyncVO syncCatalog();

    /**
     * 查询基金全集缓存状态
     *
     * @return 缓存状态
     */
    FundCatalogStatusVO catalogStatus();
}
