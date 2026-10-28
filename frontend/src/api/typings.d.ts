declare namespace API {
  type CalibrationGapVO = {
    /** 品种业务主键 */
    varietyKey?: string;
    /** 6 位代码 */
    varietyCode?: string;
    /** 标的名称 */
    varietyName?: string;
    /** 未能校准的原因 */
    reason?: string;
  };

  type CalibrationItemVO = {
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
    /** 交易日 */
    tradeDate?: string;
    /** 当时输出档位 */
    outputTier?: "A" | "B" | "C";
    /** 当时给出的预估涨跌幅，百分点；B、C 档为 null */
    estimatedChange?: number;
    /** 当日实际涨跌幅，百分点 */
    actualChange?: number;
    /** 误差，预估减实际，百分点 */
    errorValue?: number;
    /** 本次判定所用阈值，百分点；混合型为 null */
    thresholdValue?: number;
    /** 是否超阈值 */
    overThreshold?: boolean;
    /** 方向是否命中，混合型才有 */
    directionHit?: boolean;
    /** 实际值是否落在区间内，混合型才有 */
    inInterval?: boolean;
    /** 当时区间下限，百分点 */
    intervalLow?: number;
    /** 当时区间上限，百分点 */
    intervalHigh?: number;
    /** 当时区间宽度，百分点 */
    intervalWidth?: number;
    /** 校准后的滚动档位 */
    rollingTier?: "A" | "B" | "C";
    /** 滚动窗口内绝对误差的中位数，百分点 */
    medianAbsError?: number;
    /** 滚动窗口内的方向准确率 */
    directionAccuracy?: number;
    /** 滚动窗口内的区间覆盖率 */
    intervalCoverage?: number;
    /** 连续超阈值期数 */
    consecutiveOverCount?: number;
    /** 是否建议停用该基金预估 */
    suggestSuspend?: boolean;
    /** 黄金 β，非黄金为 null */
    beta?: number;
    /** 黄金 AR(1) 系数，非黄金为 null */
    rho?: number;
    /** 黄金残差 P90，百分点 */
    residualP90?: number;
    /** 当时持仓截止日 */
    holdingCutoffDate?: string;
    /** 当时持仓披露日 */
    holdingDisclosureDate?: string;
    /** 预估来源，SNAPSHOT 或 RECOMPUTED */
    estimateSource?: string;
    /** 本条的说明文字，每条一句 */
    remarks?: string[];
  };

  type CalibrationRequest = {
    /** 待校准交易日，为空时取当日 */
    tradeDate?: string;
    /** 指定基金，为空时按全部持有中的持仓 */
    varietyKeys?: string[];
    /** 是否在预估留存缺失时用当日行情重算，默认 false 只报缺口 */
    recompute?: boolean;
  };

  type CalibrationResultVO = {
    /** 校准交易日 */
    tradeDate?: string;
    /** 逐只对照结果 */
    items?: CalibrationItemVO[];
    /** 超阈值的基金，从 items 中筛出便于页面直接标红 */
    overThresholdItems?: CalibrationItemVO[];
    /** 建议停用的基金 */
    suspensionHints?: CalibrationItemVO[];
    /** 未能校准的基金，如当日预估缺失或净值尚未公布 */
    gaps?: CalibrationGapVO[];
    /** 校准只数 */
    calibratedCount?: number;
    /** 超阈值只数 */
    overThresholdCount?: number;
    /** 未能校准只数 */
    gapCount?: number;
    /** 净利润落库条数，含覆盖写 */
    writtenCount?: number;
    /** 本次说明，如「当日预估缺失已跳过 2 只」 */
    notice?: string;
  };

  type DeleteRequest = {
    /** id */
    id?: number;
  };

  type DividendAdviceRequest = {
    /** 品种业务主键，如 OF:090010 */
    varietyKey?: string;
    /** 当前持有金额，单位元；留空则用持仓份额 × 最新净值 */
    currentAmount?: number;
  };

  type DividendAdviceVO = {
    /** 品种业务主键 */
    varietyKey?: string;
    /** 6 位代码 */
    varietyCode?: string;
    /** 标的名称 */
    varietyName?: string;
    /** 跟踪指数代码 */
    indexCode?: string;
    /** 动作 */
    action?: "ADD" | "REDUCE" | "HOLD" | "UNDECIDABLE";
    /** 动作中文名，供页面与邮件回显 */
    actionLabel?: string;
    /** 建议买卖金额，单位元，精确到元 */
    suggestAmount?: number;
    /** 目标持仓金额，单位元 */
    targetAmount?: number;
    /** 当前持有金额，单位元 */
    currentAmount?: number;
    /** 减仓后的剩余金额，单位元 */
    remainingAmount?: number;
    /** 基准仓位金额，单位元 */
    baseAmount?: number;
    /** 保底分红金额，单位元 */
    floorAmount?: number;
    /** 使用的股息率，单位百分点 */
    dividendYield?: number;
    /** 股息率数据日期，页面与邮件必须显示 */
    dataDate?: string;
    /** 距离今天已过天数 */
    ageDays?: number;
    /** 股息率是否已过期 */
    stale?: boolean;
    /** 是否因保底分红金额放弃了减仓 */
    floorProtected?: boolean;
    /** 是否压制了同标的的网格提醒 */
    gridSuppressed?: boolean;
    /** 网格互斥说明，同一标的不出现冲突建议的口径 */
    gridNote?: string;
    /** 判断依据文字 */
    remarks?: string[];
    /** 当前金额的取数说明，手工指定与按净值推算不同 */
    amountSource?: string;
  };

  type DividendAdvisoryVO = {
    /** 逐只建议，按建议金额降序 */
    items?: DividendAdviceVO[];
    /** 无建议的品种，逐只带原因 */
    skippedItems?: DividendAdviceVO[];
    /** 本次纳入的品种数 */
    requested?: number;
    /** 给出加减仓动作的品种数 */
    actionCount?: number;
    /** 维持持有的品种数 */
    holdCount?: number;
    /** 不给动作的品种数 */
    undecidableCount?: number;
    /** 建议买入合计，单位元 */
    totalAddAmount?: number;
    /** 建议卖出合计，单位元 */
    totalReduceAmount?: number;
    /** 当前红利持仓合计金额，单位元 */
    totalCurrentAmount?: number;
    /** 参数是否可用，false 时整批不给动作 */
    paramUsable?: boolean;
    /** 参数问题清单 */
    paramIssues?: string[];
    /** 基准仓位金额，单位元 */
    baseAmount?: number;
    /** 保底分红金额，单位元 */
    floorAmount?: number;
    /** 加仓股息率阈值，单位百分点 */
    addYield?: number;
    /** 减仓股息率阈值，单位百分点 */
    reduceYield?: number;
    /** 最新股息率数据日期，页面与邮件必须显示 */
    dataDate?: string;
    /** 数据最旧已过天数 */
    ageDays?: number;
    /** 是否存在过期数据 */
    hasStale?: boolean;
    /** 与网格策略的互斥口径说明 */
    gridNote?: string;
    /** 操作提示，如无红利持仓 */
    notice?: string;
    /** 汇总里的动作分布说明，供邮件直接取用 */
    summary?: string[];
  };

  type DividendManualRequest = {
    /** 指数代码，如 399321 */
    indexCode?: string;
    /** 数据日期，必须由使用者填真实日期，不允许默认今天 */
    dataDate?: string;
    /** 股息率，单位百分点，如 4.08 表示 4.08% */
    dividendYield?: number;
    /** 指数中文全称，可空 */
    indexName?: string;
  };

  type DividendParamSaveRequest = {
    /** 基准仓位金额，单位元 */
    baseAmount?: number;
    /** 保底分红金额，单位元，不得高于基准仓位 */
    floorAmount?: number;
    /** 加仓股息率阈值，单位百分点 */
    addYield?: number;
    /** 减仓股息率阈值，单位百分点，必须低于加仓阈值 */
    reduceYield?: number;
  };

  type DividendPlanVO = {
    /** 基准仓位金额，单位元 */
    baseAmount?: number;
    /** 保底分红金额，单位元 */
    floorAmount?: number;
    /** 加仓股息率阈值，单位百分点 */
    addYield?: number;
    /** 减仓股息率阈值，单位百分点 */
    reduceYield?: number;
    /** 参数是否齐全且自洽 */
    usable?: boolean;
    /** 参数问题清单，可用时为空 */
    issues?: string[];
  };

  type DividendQueryRequest = {
    /** 指数代码 */
    indexCode?: string;
    /** 基金业务主键，传入时按其跟踪指数查询 */
    varietyKey?: string;
    /** 返回的最近期数，为空时取默认值 */
    limit?: number;
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

  type EstimateErrorQueryRequest = {
    /** 品种业务主键 */
    varietyKey?: string;
    /** 区间起始日，含 */
    startDate?: string;
    /** 区间截止日，含 */
    endDate?: string;
    /** 是否超阈值 */
    overThreshold?: boolean;
  };

  type EstimateErrorTrendVO = {
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
    /** 滚动窗口内的样本数 */
    sampleCount?: number;
    /** 滚动窗口内绝对误差的中位数，百分点 */
    medianAbsError?: number;
    /** 滚动窗口内的平均绝对误差，百分点 */
    mae?: number;
    /** 滚动窗口内的方向准确率 */
    directionAccuracy?: number;
    /** 滚动窗口内的区间覆盖率，混合型才有 */
    intervalCoverage?: number;
    /** 最近一次滚动判定的档位 */
    rollingTier?: "A" | "B" | "C";
    /** 最近一次校准的交易日 */
    latestTradeDate?: string;
    /** 连续超阈值期数 */
    consecutiveOverCount?: number;
    /** 是否建议停用该基金预估 */
    suggestSuspend?: boolean;
    /** 逐日误差序列，按交易日升序 */
    series?: EstimateErrorVO[];
  };

  type EstimateErrorVO = {
    /** 主键 */
    id?: number;
    /** 品种业务主键 */
    varietyKey?: string;
    /** 交易日 */
    tradeDate?: string;
    /** 预估涨跌幅 */
    estimatedChange?: number;
    /** 实际涨跌幅 */
    actualChange?: number;
    /** 误差 */
    errorValue?: number;
    /** 是否超阈值 */
    overThreshold?: boolean;
    /** 本次判定所用阈值，百分点；混合型不参与点估计判定，为 null */
    thresholdValue?: number;
    /** 方向是否命中，混合型统计用 */
    directionHit?: boolean;
    /** 实际值是否落在所给区间内，混合型统计用 */
    inInterval?: boolean;
    /** 所给区间宽度，百分点 */
    intervalWidth?: number;
    /** 重仓股披露期 */
    disclosurePeriod?: string;
    /** 当时持仓截止日 */
    holdingCutoffDate?: string;
    /** 当时持仓披露日 */
    holdingDisclosureDate?: string;
    /** 当时输出档位 */
    outputTier?: string;
    /** 预估来源，SNAPSHOT 取当日留存、RECOMPUTED 事后重算 */
    estimateSource?: string;
    /** 创建时间 */
    createTime?: string;
    /** 更新时间 */
    updateTime?: string;
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
    /** 黄金残差 P90，百分点；供 β+AR(1) 估计的交叉校验与校准留存 */
    residualP90?: number;
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

  type goldBetaParams = {
    varietyKey?: string;
    startDate?: string;
    endDate?: string;
  };

  type GoldBetaRecordVO = {
    /** 主键 */
    id?: number;
    /** 品种业务主键 */
    varietyKey?: string;
    /** 记录日 */
    tradeDate?: string;
    /** 对黄金 ETF 的滚动 β */
    beta?: number;
    /** 回归截距，百分点 */
    alpha?: number;
    /** AR(1) 误差系数 ρ */
    rho?: number;
    /** 残差绝对值的 P90，百分点 */
    residualP90?: number;
    /** 参与估计的配对样本数 */
    samples?: number;
    /** 是否因样本不足退化为代理直取 */
    fallback?: boolean;
    /** 写入时间 */
    createTime?: string;
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

  type IndexDividendOverviewVO = {
    /** 指数代码 */
    indexCode?: string;
    /** 指数中文全称 */
    indexName?: string;
    /** 最新数据日期，页面与邮件必须显示 */
    dataDate?: string;
    /** 最新股息率，单位百分点 */
    dividendYield?: number;
    /** 数据来源，取最新一期 */
    sourceLabel?: string;
    /** 距今已过天数 */
    ageDays?: number;
    /** 是否已过期，过期时不得据此给出加减仓动作 */
    stale?: boolean;
    /** 过期说明，未过期时为 null */
    staleReason?: string;
    /** 自动抓取是否可用，国证系列为 false */
    autoAvailable?: boolean;
    /** 关联的基金代码列表，即跟踪该指数的持仓 */
    fundCodes?: string[];
    /** 近期序列，按日期降序 */
    history?: IndexDividendVO[];
  };

  type IndexDividendRefreshVO = {
    /** 刷新日期 */
    refreshDate?: string;
    /** 成功写入的指数数量 */
    savedCount?: number;
    /** 自动抓取不可用、需要手工覆盖的指数数量 */
    manualRequiredCount?: number;
    /** 明细，逐只带结果与原因 */
    items?: Item[];
  };

  type IndexDividendVO = {
    /** 指数代码 */
    indexCode?: string;
    /** 指数中文全称 */
    indexName?: string;
    /** 数据日期 */
    dataDate?: string;
    /** 股息率，单位百分点 */
    dividendYield?: number;
    /** 股息率1（总股本） */
    dividendYield1?: number;
    /** 股息率2（计算用股本） */
    dividendYield2?: number;
    /** 数据来源 */
    source?: "AUTO" | "MANUAL";
    /** 数据来源中文名 */
    sourceLabel?: string;
  };

  type Item = {
    /** 指数代码 */
    indexCode?: string;
    /** 指数中文全称 */
    indexName?: string;
    /** 数据日期 */
    dataDate?: string;
    /** 股息率，单位百分点 */
    dividendYield?: number;
    /** 是否写入成功 */
    saved?: boolean;
    /** 是否需要手工覆盖，自动抓取取不到时为 true */
    manualRequired?: boolean;
    /** 结果说明，失败与跳过时给出原因 */
    message?: string;
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

  type ResultCalibrationResultVO = {
    code?: number;
    msg?: string;
    data?: CalibrationResultVO;
  };

  type ResultDividendAdviceVO = {
    code?: number;
    msg?: string;
    data?: DividendAdviceVO;
  };

  type ResultDividendAdvisoryVO = {
    code?: number;
    msg?: string;
    data?: DividendAdvisoryVO;
  };

  type ResultDividendPlanVO = {
    code?: number;
    msg?: string;
    data?: DividendPlanVO;
  };

  type ResultEstimateBatchVO = {
    code?: number;
    msg?: string;
    data?: EstimateBatchVO;
  };

  type ResultEstimateErrorTrendVO = {
    code?: number;
    msg?: string;
    data?: EstimateErrorTrendVO;
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

  type ResultIndexDividendOverviewVO = {
    code?: number;
    msg?: string;
    /** 指数股息率总览，把最新值与数据日期、是否过期一起返回 */
    data?: IndexDividendOverviewVO;
  };

  type ResultIndexDividendRefreshVO = {
    code?: number;
    msg?: string;
    data?: IndexDividendRefreshVO;
  };

  type ResultListEstimateErrorTrendVO = {
    code?: number;
    msg?: string;
    data?: EstimateErrorTrendVO[];
  };

  type ResultListEstimateErrorVO = {
    code?: number;
    msg?: string;
    data?: EstimateErrorVO[];
  };

  type ResultListGoldBetaRecordVO = {
    code?: number;
    msg?: string;
    data?: GoldBetaRecordVO[];
  };

  type ResultListHoldingVO = {
    code?: number;
    msg?: string;
    data?: HoldingVO[];
  };

  type ResultListIndexDividendOverviewVO = {
    code?: number;
    msg?: string;
    data?: IndexDividendOverviewVO[];
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

  type suspendHintsParams = {
    tradeDate?: string;
  };

  type trendParams = {
    varietyKey: string;
    days?: number;
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
