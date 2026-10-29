package com.lilac.service;

import com.lilac.domain.dto.grid.GridAdviceRequest;
import com.lilac.domain.dto.grid.GridParamSaveRequest;
import com.lilac.domain.vo.GridAdviceVO;
import com.lilac.domain.vo.GridAdvisoryVO;
import com.lilac.domain.vo.GridPlanVO;
import com.lilac.domain.vo.GridWatchVO;

import java.util.List;

/**
 * 网格到价提醒业务接口，含参数读写、逐只提醒、监控对象与试算
 */
public interface GridAdviceService {

    /**
     * 读取通用网格参数与划格结果，尚未按标的分开配置时作为兜底
     *
     * @return 参数视图
     */
    GridPlanVO getPlan();

    /**
     * 读取指定标的的网格参数与划格结果
     *
     * @param varietyKey 品种业务主键
     * @return 参数视图
     */
    GridPlanVO getPlanOf(String varietyKey);

    /**
     * 保存指定标的的网格参数，不自洽时拒绝并指出字段
     *
     * @param request 保存请求，必须带品种业务主键
     * @return 保存后的参数视图
     */
    GridPlanVO savePlan(GridParamSaveRequest request);

    /**
     * 保存通用网格参数，供尚未逐只配置时的兜底
     *
     * @param request 保存请求
     * @return 保存后的参数视图
     */
    GridPlanVO saveGlobalPlan(GridParamSaveRequest request);

    /**
     * 对全部持有中的场内 ETF 逐只判断是否触及网格线，每只用它自己的参数
     *
     * @return 提醒总览
     */
    GridAdvisoryVO adviseAll();

    /**
     * 列出网格的监控对象，说明每只 ETF 用的是哪套格子
     *
     * @return 监控对象列表
     */
    List<GridWatchVO> watchList();

    /**
     * 对指定标的试算，可手工指定当前价与参考价
     *
     * @param request 试算请求
     * @return 单只提醒
     */
    GridAdviceVO adviseOne(GridAdviceRequest request);

    /**
     * 列出通用参数下的网格线，供页面展示划格结果
     *
     * @return 网格线价格，按升序
     */
    List<java.math.BigDecimal> lines();
}
