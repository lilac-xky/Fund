package com.lilac.service;

import com.lilac.domain.dto.grid.GridAdviceRequest;
import com.lilac.domain.dto.grid.GridParamSaveRequest;
import com.lilac.domain.vo.GridAdviceVO;
import com.lilac.domain.vo.GridAdvisoryVO;
import com.lilac.domain.vo.GridPlanVO;

import java.util.List;

/**
 * 网格到价提醒业务接口，含参数读写、逐只提醒与试算
 */
public interface GridAdviceService {

    /**
     * 读取网格参数与划格结果
     *
     * @return 参数视图
     */
    GridPlanVO getPlan();

    /**
     * 保存网格参数，不自洽时拒绝并指出字段
     *
     * @param request 保存请求
     * @return 保存后的参数视图
     */
    GridPlanVO savePlan(GridParamSaveRequest request);

    /**
     * 对全部持有中的场内 ETF 逐只判断是否触及网格线
     *
     * @return 提醒总览
     */
    GridAdvisoryVO adviseAll();

    /**
     * 对指定标的试算，可手工指定当前价与参考价
     *
     * @param request 试算请求
     * @return 单只提醒
     */
    GridAdviceVO adviseOne(GridAdviceRequest request);

    /**
     * 列出当前参数下的网格线，供页面展示划格结果
     *
     * @return 网格线价格，按升序
     */
    List<java.math.BigDecimal> lines();
}
