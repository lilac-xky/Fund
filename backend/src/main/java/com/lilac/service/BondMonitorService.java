package com.lilac.service;

import com.lilac.domain.dto.bond.BondMonitorRequest;
import com.lilac.domain.dto.bond.BondParamSaveRequest;
import com.lilac.domain.vo.BondAdvisoryVO;
import com.lilac.domain.vo.BondMonitorVO;
import com.lilac.domain.vo.BondPlanVO;

/**
 * 债券监控业务接口，按历史净值判定横盘与趋势转弱并给出换仓提醒
 */
public interface BondMonitorService {

    /**
     * 读取债券监控参数，含问题清单
     *
     * @return 参数视图
     */
    BondPlanVO getPlan();

    /**
     * 保存债券监控参数，不合法时拒绝并指出字段
     *
     * @param request 参数保存请求
     * @return 保存后的参数视图
     */
    BondPlanVO savePlan(BondParamSaveRequest request);

    /**
     * 对全部持有中的债券持仓逐只判定横盘与趋势
     *
     * @return 监控总览
     */
    BondAdvisoryVO adviseAll();

    /**
     * 对指定品种判定，供页面逐只核对
     *
     * @param request 试算请求
     * @return 单只监控结果
     */
    BondMonitorVO adviseOne(BondMonitorRequest request);
}
