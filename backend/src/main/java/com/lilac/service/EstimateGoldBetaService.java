package com.lilac.service;

import com.lilac.domain.vo.GoldBetaRecordVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 黄金 β 记录业务接口，逐日留存滚动 β 与 ρ 供 β+AR(1) 估计复用与交叉校验
 */
public interface EstimateGoldBetaService {

    /**
     * 按「基金 + 交易日」存在则更新、不存在则新增
     *
     * @param record 记录
     * @return 是否成功
     */
    boolean save(GoldBetaRecordVO record);

    /**
     * 查询黄金 β 记录，条件为空时不参与过滤
     *
     * @param varietyKey 品种业务主键，可为 null
     * @param startDate  起始日，可为 null
     * @param endDate    截止日，可为 null
     * @return 记录列表，按交易日升序
     */
    List<GoldBetaRecordVO> list(String varietyKey, LocalDate startDate, LocalDate endDate);

    /**
     * 取某只基金最近一条 β 记录
     *
     * @param varietyKey 品种业务主键
     * @return 记录，无记录返回 null
     */
    GoldBetaRecordVO latest(String varietyKey);
}
