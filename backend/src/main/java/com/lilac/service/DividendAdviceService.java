package com.lilac.service;

import com.lilac.domain.dto.dividend.DividendAdviceRequest;
import com.lilac.domain.dto.dividend.DividendParamSaveRequest;
import com.lilac.domain.vo.DividendAdviceVO;
import com.lilac.domain.vo.DividendAdvisoryVO;
import com.lilac.domain.vo.DividendPlanVO;
import com.lilac.domain.vo.DividendWatchVO;

import java.util.List;

/**
 * 红利加减仓建议业务接口，负责取股息率、按基金装配参数并给出精确到元的建议
 */
public interface DividendAdviceService {

    /**
     * 读取指定基金的红利参数，含问题清单
     *
     * @param varietyKey 品种业务主键
     * @return 参数视图
     */
    DividendPlanVO getPlan(String varietyKey);

    /**
     * 保存指定基金的红利参数，不合法时拒绝并指出字段
     *
     * @param request 保存请求
     * @return 保存后的参数视图
     */
    DividendPlanVO savePlan(DividendParamSaveRequest request);

    /**
     * 清空指定基金的红利参数，回到「未配置」状态
     *
     * @param varietyKey 品种业务主键
     * @return 是否清掉了至少一条
     */
    boolean removePlan(String varietyKey);

    /**
     * 按全部持有中的红利持仓给建议
     *
     * @return 建议总览
     */
    DividendAdvisoryVO adviseAll();

    /**
     * 按指定品种给建议
     *
     * @param request 建议请求
     * @return 单只建议
     */
    DividendAdviceVO adviseOne(DividendAdviceRequest request);

    /**
     * 列出红利监控对象，每只带出跟踪指数、股息率与它自己的参数
     *
     * @return 监控对象列表
     */
    List<DividendWatchVO> watchList();
}
