package com.lilac.service;

import com.lilac.domain.dto.holding.HoldingBuyRequest;
import com.lilac.domain.dto.holding.HoldingChangeQueryRequest;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.holding.HoldingSellRequest;
import com.lilac.domain.vo.HoldingChangeVO;
import com.lilac.domain.vo.HoldingOverviewVO;
import com.lilac.domain.vo.HoldingSummaryVO;
import com.lilac.domain.vo.HoldingVO;

import java.util.List;

/**
 * 持仓业务接口
 */
public interface HoldingService {

    /**
     * 统一查询未删除的持仓
     *
     * @param request 查询请求
     * @return 持仓视图列表
     */
    List<HoldingVO> listHolding(HoldingQueryRequest request);

    /**
     * 买入入库，校验品种已注册、份额与成本价为正、持仓品种数不超上限
     *
     * @param request 买入请求
     * @return 新增的持仓
     */
    HoldingVO buy(HoldingBuyRequest request);

    /**
     * 卖出扣减，按先进先出扣已有持仓，全部卖完时把剩余记录置为已清仓
     *
     * @param request 卖出请求
     * @return 变动后该品种的剩余持仓
     */
    List<HoldingVO> sell(HoldingSellRequest request);

    /**
     * 查询持仓变动历史，按时间倒序
     *
     * @param request 查询请求
     * @return 变动历史列表
     */
    List<HoldingChangeVO> listChanges(HoldingChangeQueryRequest request);

    /**
     * 持仓总览，按品种聚合持有中持仓并带出名称与最新净值
     *
     * @return 总览视图
     */
    HoldingOverviewVO overview();

    /**
     * 按品种业务主键汇总持有中份额与加权成本，供消歧候选回显
     *
     * @param varietyKey 品种业务主键
     * @return 汇总视图，无持仓返回 holding=false
     */
    HoldingSummaryVO summaryOf(String varietyKey);
}
