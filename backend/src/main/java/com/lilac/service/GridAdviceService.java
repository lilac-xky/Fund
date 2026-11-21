package com.lilac.service;

import com.lilac.domain.dto.grid.GridAdviceRequest;
import com.lilac.domain.dto.grid.GridParamSaveRequest;
import com.lilac.domain.vo.GridAdviceVO;
import com.lilac.domain.vo.GridAdvisoryVO;
import com.lilac.domain.vo.GridPlanVO;
import com.lilac.domain.vo.GridWatchVO;

import java.util.List;

/**
 * 网格到价提醒业务接口，参数按标的各存一套，先选标的再配格子
 */
public interface GridAdviceService {

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
     * 清除指定标的的网格参数，回到未配置状态
     *
     * @param varietyKey 品种业务主键
     * @return 是否清掉了至少一条
     */
    boolean removePlan(String varietyKey);

    /**
     * 对全部持有中的标的逐只判断是否触及网格线，每只用它自己的参数
     *
     * @return 提醒总览
     */
    GridAdvisoryVO adviseAll();

    /**
     * 列出网格的监控对象，说明每只标的用的是哪套格子、当前价落在区间内外
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
     * 列出指定标的的网格线，供页面展示划格结果
     *
     * @param varietyKey 品种业务主键
     * @return 网格线价格，按升序
     */
    List<java.math.BigDecimal> lines(String varietyKey);
}
