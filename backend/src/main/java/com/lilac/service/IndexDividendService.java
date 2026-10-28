package com.lilac.service;

import com.lilac.domain.dto.dividend.DividendManualRequest;
import com.lilac.domain.dto.dividend.DividendQueryRequest;
import com.lilac.domain.vo.IndexDividendOverviewVO;
import com.lilac.domain.vo.IndexDividendRefreshVO;

import java.util.List;

/**
 * 指数股息率业务接口，负责自动抓取、手工覆盖与新鲜度判定
 */
public interface IndexDividendService {

    /**
     * 刷新指定指数的股息率，留空时刷新全部已登记红利基金的跟踪指数
     *
     * @param indexCodes 指数代码列表，为空表示按库内红利持仓推导
     * @return 刷新结果
     */
    IndexDividendRefreshVO refresh(List<String> indexCodes);

    /**
     * 手工录入股息率，同一指数同一日期覆盖自动抓取结果
     *
     * @param request 手工录入请求
     * @return 落库后的视图
     */
    IndexDividendOverviewVO saveManual(DividendManualRequest request);

    /**
     * 查询股息率，条件为空时返回全部指数各自的最新一期
     *
     * @param request 查询请求
     * @return 总览列表
     */
    List<IndexDividendOverviewVO> listOverview(DividendQueryRequest request);
}
