declare namespace API {
  type DeleteRequest = {
    /** id */
    id?: number;
  };

  type EstimateBatchRequest = {
    /** 品种业务主键列表，为空时按全部 HOLDING 状态的持仓预估 */
    varietyKeys?: string[];
    /** 是否跳过缓存强制重算 */
    refresh?: boolean;
  };

  type EstimateBatchVO = {
    /** 可预估的基金，按预估涨跌幅降序 */
    items?: EstimateVO[];
    /** 不可预估的基金，逐只带出原因 */
    unavailableItems?: EstimateVO[];
    /** 本次预估的基金数 */
    requested?: number;
    /** 可预估的基金数 */
    estimableCount?: number;
    /** 不可预估的基金数 */
    unavailableCount?: number;
    /** 整体预估涨跌幅，按持仓等权平均，百分点；只统计可预估且为 A 档的基金 */
    overallChange?: number;
    /** 参与整体统计的基金数 */
    overallCount?: number;
    /** 行情数据日期 */
    dataDate?: string;
    /** 是否处于交易时段 */
    tradingHours?: boolean;
    /** 是否有基金走了降级缓存 */
    degraded?: boolean;
    /** 混合型基金数，页面据此提示「仅方向参考」 */
    mixedCount?: number;
    /** 整体涨跌的口径说明，只统计 A 档时给出提示 */
    overallNote?: string;
    /** 操作提示，如超上限已分批 */
    notice?: string;
  };

  type EstimateVO = {
    /** 品种业务主键 */
    varietyKey?: string;
    /** 6 位代码 */
    varietyCode?: string;
    /** 标的名称 */
    varietyName?: string;
    /** 基金类型 */
    category?:
      | "STOCK"
      | "MIXED"
      | "BOND"
      | "GOLD"
      | "DIVIDEND"
      | "ETF"
      | "UNKNOWN";
    /** 基金类型中文名 */
    categoryLabel?: string;
    /** 本次走的分支 */
    branch?:
      | "INDEX_ONLY"
      | "STOCK_WEIGHTED"
      | "GOLD_PROXY"
      | "BOND_PROXY"
      | "UNAVAILABLE";
    /** 分支中文名 */
    branchLabel?: string;
    /** 输出档位 */
    tier?: "A" | "B" | "C";
    /** 档位中文名 */
    tierLabel?: string;
    /** 可信度 */
    confidence?: "HIGH" | "MEDIUM" | "LOW";
    /** 可信度中文名 */
    confidenceLabel?: string;
    /** 预估涨跌幅，百分点；仅 A 档有值，B 档与 C 档为 null */
    estimatedChange?: number;
    /** 涨跌方向，B 档与部分 C 档使用 */
    direction?: "UP" | "DOWN" | "FLAT";
    /** 方向中文名 */
    directionLabel?: string;
    /** 区间下限，百分点，仅 B 档 */
    intervalLow?: number;
    /** 区间上限，百分点，仅 B 档 */
    intervalHigh?: number;
    /** 参与加权的覆盖率，百分点 */
    coverage?: number;
    /** 持仓报告期 */
    holdingPeriod?: string;
    /** 持仓截止日 */
    holdingCutoffDate?: string;
    /** 持仓披露日 */
    holdingDisclosureDate?: string;
    /** 净值数据日期 */
    netWorthDate?: string;
    /** 行情数据日期 */
    quoteDate?: string;
    /** 行情是否来自交易时段内 */
    realtime?: boolean;
    /** 代理标的完整代码 */
    proxySymbol?: string;
    /** 黄金 β */
    beta?: number;
    /** 黄金 AR(1) 系数 */
    rho?: number;
    /** 是否来自降级缓存 */
    degraded?: boolean;
    /** 是否可预估 */
    estimable?: boolean;
    /** 不可预估原因，可预估时为 null */
    unavailableReason?: string;
    /** 不可预估细节，如停牌标的名单 */
    unavailableDetail?: string;
    /** 同屏说明文字，每条一句 */
    remarks?: string[];
  };

  type ForexRateVO = {
    /** 汇率对，如 HKDCNY */
    pair?: string;
    /** 最新汇率 */
    rate?: number;
    /** 上一有效交易日的汇率 */
    previousRate?: number;
    /** 当日变动，小数形式 */
    changeRate?: number;
    /** 汇率数据日期 */
    tradeDate?: string;
    /** 取数来源 */
    source?: string;
    /** 是否可用 */
    usable?: boolean;
  };

  type FundCandidateVO = {
    /** 业务主键，如 OF:000001 */
    varietyKey?: string;
    /** 品种类型 */
    varietyType?: "OF" | "ETF" | "IDX";
    /** 品种类型中文名 */
    varietyTypeLabel?: string;
    /** 6 位代码 */
    varietyCode?: string;
    /** 标的名称 */
    varietyName?: string;
    /** 基金类型 */
    category?:
      | "STOCK"
      | "MIXED"
      | "BOND"
      | "GOLD"
      | "DIVIDEND"
      | "ETF"
      | "UNKNOWN";
    /** 基金类型中文名 */
    categoryLabel?: string;
    /** 类型是否已确定，false 表示须使用者手动指定 */
    confirmed?: boolean;
    /** 跟踪指数代码，红利基金用于查股息率 */
    trackIndexCode?: string;
    /** 是否可从持仓入口录入，指数与股票为 false */
    recordable?: boolean;
    /** 身份提示，说明判定依据或需要注意的地方 */
    note?: string;
  };

  type FundCatalogStatusVO = {
    /** 缓存总条数 */
    total?: number;
    /** 最近一次同步时间 */
    syncTime?: string;
    /** 是否已过期，过期时下次消歧会触发同步 */
    stale?: boolean;
    /** 允许的最大缓存天数 */
    maxAgeDays?: number;
  };

  type FundCatalogSyncVO = {
    /** 同步后的总条数 */
    total?: number;
    /** 同步完成时间 */
    syncTime?: string;
    /** 同步耗时毫秒 */
    costMs?: number;
  };

  type FundConfirmRequest = {
    /** 6 位代码 */
    code?: string;
    /** 品种类型，只能是场外基金或场内 ETF */
    varietyType?: "OF" | "ETF" | "IDX";
    /** 基金类型，自动判定为待确认时必填 */
    category?:
      | "STOCK"
      | "MIXED"
      | "BOND"
      | "GOLD"
      | "DIVIDEND"
      | "ETF"
      | "UNKNOWN";
    /** 跟踪指数代码，红利基金必填以取股息率 */
    trackIndexCode?: string;
  };

  type FundDisambiguateRequest = {
    /** 6 位基金或在市代码 */
    code?: string;
  };

  type FundDisambiguateVO = {
    /** 查询的 6 位代码 */
    code?: string;
    /** 是否命中任何候选 */
    exists?: boolean;
    /** 候选列表，按场外基金、场内 ETF、指数顺序 */
    candidates?: FundCandidateVO[];
    /** 名称相近的其他份额，A/C 类并列展示供核对 */
    relatedShareClasses?: FundCandidateVO[];
    /** 同码股票提示，股票不从持仓入口录入 */
    stockHint?: string;
    /** 是否存在类型待确认的候选 */
    needConfirm?: boolean;
    /** 是否降级，指数校验失败时为 true */
    degraded?: boolean;
    /** 基金全集缓存的数据时间 */
    catalogUpdatedAt?: string;
  };

  type FundSnapshotVO = {
    /** 6 位基金代码 */
    code?: string;
    /** 基金简称 */
    name?: string;
    /** 净值数据日期 */
    netWorthDate?: string;
    /** 持仓截止日 */
    holdingCutoffDate?: string;
    /** 持仓披露日 */
    holdingDisclosureDate?: string;
    /** 季末仓位报告期截止日，无股票持仓时用它表示数据的报告期 */
    allocationReportDate?: string;
    /** 报告期，如 2026Q2 */
    holdingPeriod?: string;
    /** 重仓股列表，按权重降序 */
    stockHoldings?: HoldingItemVO[];
    /** 债券持仓列表 */
    bondHoldings?: HoldingItemVO[];
    /** 重仓股合计占比，单位百分点；季报只有前十大时明显低于股票仓位 */
    totalStockWeight?: number;
    /** 债券持仓合计占比，单位百分点 */
    totalBondWeight?: number;
    /** 是否全量披露 */
    fullDisclosure?: boolean;
    /** 季末股票占净比 */
    stockRatio?: number;
    /** 季末债券占净比 */
    bondRatio?: number;
    /** 季末现金占净比 */
    cashRatio?: number;
    /** 是否可用于当日预估 */
    estimable?: boolean;
    /** 不可预估的具体原因，可预估时为 null */
    unavailableReason?: string;
    /** 是否走了降级缓存 */
    degraded?: boolean;
    /** 降级时的数据截止时间 */
    dataCutoffDate?: string;
    /** 净值序列，按日期升序 */
    netWorthPoints?: NetWorthPointVO[];
  };

  type getByIdParams = {
    id?: number;
  };

  type getByIdParams = {
    id?: number;
  };

  type getByIdParams = {
    id?: number;
  };

  type historyParams = {
    codes: string[];
    limit?: number;
  };

  type HoldingAddRequest = {
    /** 所持品种的业务主键 */
    varietyKey?: string;
    /** 持有份额 */
    shares?: number;
    /** 成本价 */
    costPrice?: number;
    /** 建仓日期 */
    openDate?: string;
    /** 持仓状态 */
    status?: "HOLDING" | "CLOSED";
  };

  type HoldingItemVO = {
    /** 证券代码 */
    code?: string;
    /** 证券名称 */
    name?: string;
    /** 占净值比例，单位百分点 */
    weight?: number;
  };

  type HoldingQueryRequest = {
    /** 品种业务主键 */
    varietyKey?: string;
    /** 持仓状态 */
    status?: "HOLDING" | "CLOSED";
  };

  type HoldingUpdateRequest = {
    /** 主键 */
    id?: number;
    /** 持有份额 */
    shares?: number;
    /** 成本价 */
    costPrice?: number;
    /** 建仓日期 */
    openDate?: string;
    /** 持仓状态 */
    status?: "HOLDING" | "CLOSED";
  };

  type HoldingVO = {
    /** 主键 */
    id?: number;
    /** 所持品种的业务主键 */
    varietyKey?: string;
    /** 持有份额 */
    shares?: number;
    /** 成本价 */
    costPrice?: number;
    /** 建仓日期 */
    openDate?: string;
    /** 持仓状态 */
    status?: "HOLDING" | "CLOSED";
    /** 创建时间 */
    createTime?: string;
    /** 更新时间 */
    updateTime?: string;
  };

  type MarketBarVO = {
    /** 交易日 */
    date?: string;
    /** 收盘价 */
    close?: number;
  };

  type MarketHistoryVO = {
    /** 带交易所前缀的完整代码 */
    symbol?: string;
    /** 日线，按日期升序 */
    bars?: MarketBarVO[];
    /** 根数 */
    size?: number;
    /** 最新交易日 */
    latestDate?: string;
    /** 最新收盘价 */
    latestClose?: number;
    /** 是否前复权口径 */
    adjusted?: boolean;
    /** 取数来源 */
    source?: string;
  };

  type MarketQuoteVO = {
    /** 带交易所前缀的完整代码 */
    symbol?: string;
    /** 标的名称 */
    name?: string;
    /** 最新价 */
    price?: number;
    /** 昨收 */
    previousClose?: number;
    /** 当日涨跌幅，单位百分点 */
    changePercent?: number;
    /** 数据日期 */
    tradeDate?: string;
    /** 行情时间 */
    quoteTime?: string;
    /** 是否交易时段内的实时数据，false 表示上一交易日 */
    realtime?: boolean;
    /** 可用性状态 */
    status?: "OK" | "SUSPENDED" | "NO_DATA" | "UNAVAILABLE";
    /** 是否可用于预估 */
    usable?: boolean;
    /** 不可用原因 */
    unavailableReason?: string;
    /** 取数来源 */
    source?: string;
    /** 是否前复权口径 */
    adjusted?: boolean;
  };

  type NetWorthPointVO = {
    /** 净值日期 */
    netWorthDate?: string;
    /** 单位净值 */
    unitNetWorth?: number;
    /** 当日净值回报，单位百分点 */
    equityReturn?: number;
  };

  type ParamQueryRequest = {
    /** 参数分组 */
    paramGroup?: "DIVIDEND" | "GRID" | "INDICATOR" | "ALERT";
    /** 参数名 */
    paramKey?: string;
  };

  type ParamSaveRequest = {
    /** 参数分组 */
    paramGroup?: "DIVIDEND" | "GRID" | "INDICATOR" | "ALERT";
    /** 参数名，组内唯一 */
    paramKey?: string;
    /** 参数值 */
    paramValue?: string;
    /** 参数说明 */
    paramDesc?: string;
  };

  type QuoteBatchRequest = {
    /** 标的代码列表，可带 sh/sz/bj/hk 前缀，不带时按号段推断 */
    codes?: string[];
    /** 日线根数，只在查历史时使用，不传取配置默认值 */
    historyLimit?: number;
  };

  type QuoteBatchVO = {
    /** 逐只行情，按请求顺序 */
    quotes?: MarketQuoteVO[];
    /** 请求的标的数 */
    requested?: number;
    /** 可用于预估的标的数 */
    usableCount?: number;
    /** 停牌的标的数 */
    suspendedCount?: number;
    /** 无行情的标的数，含 920xxx 北交所 */
    noDataCount?: number;
    /** 本次是否有标的走了备用源 */
    fallbackUsed?: boolean;
    /** 数据日期，取可用标的中最新的交易日 */
    dataDate?: string;
    /** 是否处于交易时段，false 时数据为上一交易日 */
    tradingHours?: boolean;
  };

  type ResultBoolean = {
    code?: number;
    msg?: string;
    data?: boolean;
  };

  type ResultEstimateBatchVO = {
    code?: number;
    msg?: string;
    data?: EstimateBatchVO;
  };

  type ResultEstimateVO = {
    code?: number;
    msg?: string;
    data?: EstimateVO;
  };

  type ResultForexRateVO = {
    code?: number;
    msg?: string;
    data?: ForexRateVO;
  };

  type ResultFundCatalogStatusVO = {
    code?: number;
    msg?: string;
    data?: FundCatalogStatusVO;
  };

  type ResultFundCatalogSyncVO = {
    code?: number;
    msg?: string;
    data?: FundCatalogSyncVO;
  };

  type ResultFundDisambiguateVO = {
    code?: number;
    msg?: string;
    data?: FundDisambiguateVO;
  };

  type ResultFundSnapshotVO = {
    code?: number;
    msg?: string;
    data?: FundSnapshotVO;
  };

  type ResultHoldingVO = {
    code?: number;
    msg?: string;
    /** 持仓视图对象 */
    data?: HoldingVO;
  };

  type ResultListHoldingVO = {
    code?: number;
    msg?: string;
    data?: HoldingVO[];
  };

  type ResultListMarketHistoryVO = {
    code?: number;
    msg?: string;
    data?: MarketHistoryVO[];
  };

  type ResultListStrategyParamVO = {
    code?: number;
    msg?: string;
    data?: StrategyParamVO[];
  };

  type ResultListVarietyVO = {
    code?: number;
    msg?: string;
    data?: VarietyVO[];
  };

  type ResultMarketQuoteVO = {
    code?: number;
    msg?: string;
    data?: MarketQuoteVO;
  };

  type ResultQuoteBatchVO = {
    code?: number;
    msg?: string;
    data?: QuoteBatchVO;
  };

  type ResultStrategyParamVO = {
    code?: number;
    msg?: string;
    /** 策略参数视图对象 */
    data?: StrategyParamVO;
  };

  type ResultVarietyVO = {
    code?: number;
    msg?: string;
    data?: VarietyVO;
  };

  type singleParams = {
    code: string;
  };

  type singleParams = {
    varietyKey: string;
  };

  type snapshotParams = {
    code: string;
  };

  type StrategyParamVO = {
    /** 主键 */
    id?: number;
    /** 参数分组 */
    paramGroup?: "DIVIDEND" | "GRID" | "INDICATOR" | "ALERT";
    /** 参数名 */
    paramKey?: string;
    /** 参数值 */
    paramValue?: string;
    /** 参数说明 */
    paramDesc?: string;
    /** 创建时间 */
    createTime?: string;
    /** 更新时间 */
    updateTime?: string;
  };

  type VarietyQueryRequest = {
    /** 业务主键 */
    varietyKey?: string;
    /** 6 位代码 */
    varietyCode?: string;
    /** 品种类型 */
    varietyType?: "OF" | "ETF" | "IDX";
    /** 基金类型 */
    category?:
      | "STOCK"
      | "MIXED"
      | "BOND"
      | "GOLD"
      | "DIVIDEND"
      | "ETF"
      | "UNKNOWN";
    /** 是否已确认类型 */
    confirmed?: boolean;
  };

  type VarietySaveRequest = {
    /** 业务主键，形如 OF:000001 */
    varietyKey?: string;
    /** 代码所属品种类型 */
    varietyType?: "OF" | "ETF" | "IDX";
    /** 6 位代码 */
    varietyCode?: string;
    /** 标的名称 */
    varietyName?: string;
    /** 基金类型 */
    category?:
      | "STOCK"
      | "MIXED"
      | "BOND"
      | "GOLD"
      | "DIVIDEND"
      | "ETF"
      | "UNKNOWN";
    /** 跟踪指数代码 */
    trackIndexCode?: string;
    /** 类型是否已确认 */
    confirmed?: boolean;
  };

  type VarietyVO = {
    /** 主键 */
    id?: number;
    /** 业务主键 */
    varietyKey?: string;
    /** 品种类型 */
    varietyType?: "OF" | "ETF" | "IDX";
    /** 6 位代码 */
    varietyCode?: string;
    /** 标的名称 */
    varietyName?: string;
    /** 基金类型 */
    category?:
      | "STOCK"
      | "MIXED"
      | "BOND"
      | "GOLD"
      | "DIVIDEND"
      | "ETF"
      | "UNKNOWN";
    /** 跟踪指数代码 */
    trackIndexCode?: string;
    /** 类型是否已确认 */
    confirmed?: boolean;
    /** 创建时间 */
    createTime?: string;
    /** 更新时间 */
    updateTime?: string;
  };
}
