package com.lilac.engine.model;

import com.lilac.adapter.model.ForexRate;
import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.domain.vo.FundSnapshotVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.engine.BondBaselineEstimator;
import com.lilac.enums.EstimateTier;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 一只基金预估所需的全部输入，由服务层装配，引擎只做计算
 */
@Data
public class EstimateContext {

    /**
     * 品种信息，带类型与跟踪指数
     */
    private VarietyVO variety;

    /**
     * 天天基金的原始类型，用于判断是否被动指数型
     */
    private String rawFundType;

    /**
     * 净值与持仓，可为 null；取不到时持仓类分支判为不可预估
     */
    private FundSnapshotVO snapshot;

    /**
     * 解析出的代理标的完整代码，如 sh000300、sh518880
     */
    private String proxySymbol;

    /**
     * 代理标的当日行情，解析不到时为 null
     */
    private MarketQuote proxyQuote;

    /**
     * 代理标的不可用的原因，可用时为 null
     */
    private String proxyUnavailableReason;

    /**
     * 黄金 β 与 AR(1) 的滚动估计结果，仅黄金分支使用
     */
    private GoldBetaModel goldModel;

    /**
     * 债券传导系数估计结果，仅债券分支使用；为 null 表示按 1 倍代理
     */
    private BondBaselineEstimator.Model bondModel;

    /**
     * 持仓标的当日行情，按带前缀完整代码索引
     */
    private Map<String, MarketQuote> holdingQuotes;

    /**
     * 港币兑人民币汇率，无港股持仓时为 null
     */
    private ForexRate forex;

    /**
     * 该基金的历史净值序列，按日期升序，黄金 β 估计用
     */
    private List<FundNetWorthPoint> netWorthPoints;

    /**
     * 历史误差记录，按交易日升序，档位与可信度判定用
     */
    private List<EstimateErrorVO> history;

    /**
     * 最近一次记录的输出档位，用于档位升降的滞回
     */
    private EstimateTier previousTier;

    /**
     * 持仓中是否含港股
     */
    private boolean hkHoldings;

    /**
     * 该基金快照是否来自降级缓存
     */
    private boolean degraded;
}
