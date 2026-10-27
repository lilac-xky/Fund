package com.lilac.service;

import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.vo.FundCatalogStatusVO;
import com.lilac.domain.vo.FundCatalogSyncVO;

import java.util.List;

/**
 * 基金全集缓存业务接口
 */
public interface FundCatalogService {

    /**
     * 从上游全量拉取并覆盖本地缓存
     *
     * @return 同步结果
     */
    FundCatalogSyncVO sync();

    /**
     * 缓存过期时同步，未过期直接返回
     *
     * @return 同步结果
     */
    FundCatalogSyncVO syncIfStale();

    /**
     * 查询缓存状态
     *
     * @return 缓存状态
     */
    FundCatalogStatusVO status();

    /**
     * 按 6 位代码查缓存
     *
     * @param code 6 位基金代码
     * @return 缓存行，未命中返回 null
     */
    FundCatalog findByCode(String code);

    /**
     * 按名称关键词模糊查缓存，用于 A/C 类并列展示
     *
     * @param nameKeyword 名称关键词
     * @param limit       返回条数上限
     * @return 缓存行列表
     */
    List<FundCatalog> searchByName(String nameKeyword, int limit);
}
