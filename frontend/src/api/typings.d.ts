declare namespace API {
  type AuthStatusVO = {
    /** 是否已登录 */
    authenticated?: boolean
    /** 登录开关是否开启，关闭时页面不做跳转 */
    required?: boolean
    /** 凭据是否已配置，未配置时任何人都登不进来 */
    configured?: boolean
    /** 会话剩余有效秒数，未登录为 0 */
    remainingSeconds?: number
  }

  type BondAdvisoryVO = {
    /** 触发换仓提醒的基金 */
    items?: BondMonitorVO[]
    /** 未触发提醒的基金，逐只带出当前数值 */
    idleItems?: BondMonitorVO[]
    /** 本次纳入的基金数 */
    requested?: number
    /** 触发提醒的基金数 */
    alertCount?: number
    /** 未触发的基金数 */
    idleCount?: number
    /** 数据不足无法判断的基金数 */
    unavailableCount?: number
    /** 参数是否可用，false 时整批不给结论 */
    paramUsable?: boolean
    /** 参数问题清单 */
    paramIssues?: string[]
    /** 横盘天数阈值，供页面回显 */
    sidewaysDaysThreshold?: number
    /** 转弱斜率阈值，百分点 */
    weakSlopeThreshold?: number
    /** 逐条摘要，供邮件直接取用 */
    summary?: string[]
    /** 操作提示，如无债券持仓 */
    notice?: string
  }

  type BondMonitorRequest = {
    /** 品种业务主键 */
    varietyKey?: string
  }

  type BondMonitorVO = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 基金类型中文名 */
    categoryLabel?: string
    /** 信号，横盘、趋势转弱、正常或数据不足 */
    signal?: 'SIDEWAYS' | 'WEAK' | 'NORMAL' | 'UNAVAILABLE'
    /** 信号中文名 */
    signalLabel?: string
    /** 是否产生换仓提醒 */
    alerting?: boolean
    /** 连续横盘的交易日数 */
    sidewaysDays?: number
    /** 横盘区间起点日期 */
    sidewaysStart?: string
    /** 横盘区间终点日期 */
    sidewaysEnd?: string
    /** 横盘区间的自然日跨度 */
    sidewaysCalendarDays?: number
    /** 横盘区间累计涨幅，百分点 */
    sidewaysReturn?: number
    /** 回看窗口的交易日数 */
    window?: number
    /** 回看窗口起点日期 */
    windowStart?: string
    /** 回看窗口终点日期 */
    windowEnd?: string
    /** 回看窗口累计涨幅，百分点 */
    windowReturn?: number
    /** 日收益回归斜率，百分点每日 */
    slope?: number
    /** 年化斜率，百分点每年 */
    annualizedSlope?: number
    /** 日收益标准差，百分点 */
    volatility?: number
    /** 参与计算的净值点数 */
    samples?: number
    /** 最新净值日期 */
    latestNetWorthDate?: string
    /** 当前持有金额，单位元 */
    currentAmount?: number
    /** 当前金额来源说明 */
    amountSource?: string
    /** 判定依据文字，逐条带数值 */
    remarks?: string[]
    /** 数据不足时的原因 */
    unavailableReason?: string
  }

  type BondParamSaveRequest = {
    /** 回看窗口，交易日 */
    window?: number
    /** 判定横盘的单日波动上限，百分点 */
    sidewaysBand?: number
    /** 触发换仓提醒的横盘交易日数 */
    sidewaysDays?: number
    /** 横盘期累计涨幅下限，百分点 */
    sidewaysReturn?: number
    /** 年化斜率转弱阈值，百分点 */
    weakSlope?: number
    /** 判定趋势转弱的波动率下限，百分点 */
    weakVolatility?: number
  }

  type BondPlanVO = {
    /** 回看窗口，交易日 */
    window?: number
    /** 判定横盘的单日波动上限，百分点 */
    sidewaysBand?: number
    /** 触发换仓提醒的横盘交易日数 */
    sidewaysDays?: number
    /** 横盘期累计涨幅下限，百分点 */
    sidewaysReturn?: number
    /** 年化斜率转弱阈值，百分点 */
    weakSlope?: number
    /** 判定趋势转弱的波动率下限，百分点 */
    weakVolatility?: number
    /** 参数是否齐全且自洽 */
    usable?: boolean
    /** 参数问题清单，可用时为空 */
    issues?: string[]
  }

  type CalibrationGapVO = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 未能校准的原因 */
    reason?: string
  }

  type CalibrationItemVO = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 基金类型 */
    category?: 'STOCK' | 'MIXED' | 'BOND' | 'GOLD' | 'DIVIDEND' | 'ETF' | 'UNKNOWN'
    /** 基金类型中文名 */
    categoryLabel?: string
    /** 交易日 */
    tradeDate?: string
    /** 当时输出档位 */
    outputTier?: 'A' | 'B' | 'C'
    /** 当时给出的预估涨跌幅，百分点；B、C 档为 null */
    estimatedChange?: number
    /** 当日实际涨跌幅，百分点 */
    actualChange?: number
    /** 误差，预估减实际，百分点 */
    errorValue?: number
    /** 本次判定所用阈值，百分点；混合型为 null */
    thresholdValue?: number
    /** 是否超阈值 */
    overThreshold?: boolean
    /** 方向是否命中，混合型才有 */
    directionHit?: boolean
    /** 实际值是否落在区间内，混合型才有 */
    inInterval?: boolean
    /** 当时区间下限，百分点 */
    intervalLow?: number
    /** 当时区间上限，百分点 */
    intervalHigh?: number
    /** 当时区间宽度，百分点 */
    intervalWidth?: number
    /** 校准后的滚动档位 */
    rollingTier?: 'A' | 'B' | 'C'
    /** 滚动窗口内绝对误差的中位数，百分点 */
    medianAbsError?: number
    /** 滚动窗口内的方向准确率 */
    directionAccuracy?: number
    /** 滚动窗口内的区间覆盖率 */
    intervalCoverage?: number
    /** 连续超阈值期数 */
    consecutiveOverCount?: number
    /** 是否建议停用该基金预估 */
    suggestSuspend?: boolean
    /** 黄金 β，非黄金为 null */
    beta?: number
    /** 黄金 AR(1) 系数，非黄金为 null */
    rho?: number
    /** 黄金残差 P90，百分点 */
    residualP90?: number
    /** 当时持仓截止日 */
    holdingCutoffDate?: string
    /** 当时持仓披露日 */
    holdingDisclosureDate?: string
    /** 预估来源，SNAPSHOT 或 RECOMPUTED */
    estimateSource?: string
    /** 本条的说明文字，每条一句 */
    remarks?: string[]
  }

  type CalibrationRequest = {
    /** 待校准交易日，为空时取当日 */
    tradeDate?: string
    /** 指定基金，为空时按全部持有中的持仓 */
    varietyKeys?: string[]
    /** 是否在预估留存缺失时用当日行情重算，默认 false 只报缺口 */
    recompute?: boolean
  }

  type CalibrationResultVO = {
    /** 校准交易日 */
    tradeDate?: string
    /** 逐只对照结果 */
    items?: CalibrationItemVO[]
    /** 超阈值的基金，从 items 中筛出便于页面直接标红 */
    overThresholdItems?: CalibrationItemVO[]
    /** 建议停用的基金 */
    suspensionHints?: CalibrationItemVO[]
    /** 未能校准的基金，如当日预估缺失或净值尚未公布 */
    gaps?: CalibrationGapVO[]
    /** 校准只数 */
    calibratedCount?: number
    /** 超阈值只数 */
    overThresholdCount?: number
    /** 未能校准只数 */
    gapCount?: number
    /** 净利润落库条数，含覆盖写 */
    writtenCount?: number
    /** 校准前主动刷新成功的净值只数，用于区分「没公布」与「没抓到」 */
    netWorthRefreshed?: number
    /** 本次说明，如「当日预估缺失已跳过 2 只」 */
    notice?: string
  }

  type DividendAdviceRequest = {
    /** 品种业务主键，如 OF:090010 */
    varietyKey?: string
    /** 当前持有金额，单位元；留空则用持仓份额 × 最新净值 */
    currentAmount?: number
  }

  type DividendAdviceVO = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 跟踪指数代码 */
    indexCode?: string
    /** 动作 */
    action?: 'ADD' | 'REDUCE' | 'HOLD' | 'UNDECIDABLE'
    /** 动作中文名，供页面与邮件回显 */
    actionLabel?: string
    /** 建议买卖金额，单位元，精确到元 */
    suggestAmount?: number
    /** 目标持仓金额，单位元 */
    targetAmount?: number
    /** 当前持有金额，单位元 */
    currentAmount?: number
    /** 减仓后的剩余金额，单位元 */
    remainingAmount?: number
    /** 基准仓位金额，单位元 */
    baseAmount?: number
    /** 保底分红金额，单位元 */
    floorAmount?: number
    /** 使用的股息率，单位百分点 */
    dividendYield?: number
    /** 股息率数据日期，页面与邮件必须显示 */
    dataDate?: string
    /** 距离今天已过天数 */
    ageDays?: number
    /** 股息率是否已过期 */
    stale?: boolean
    /** 是否因保底分红金额放弃了减仓 */
    floorProtected?: boolean
    /** 是否压制了同标的的网格提醒 */
    gridSuppressed?: boolean
    /** 网格互斥说明，同一标的不出现冲突建议的口径 */
    gridNote?: string
    /** 判断依据文字 */
    remarks?: string[]
    /** 当前金额的取数说明，手工指定与按净值推算不同 */
    amountSource?: string
  }

  type DividendAdvisoryVO = {
    /** 逐只建议，按建议金额降序 */
    items?: DividendAdviceVO[]
    /** 无建议的品种，逐只带原因 */
    skippedItems?: DividendAdviceVO[]
    /** 本次纳入的品种数 */
    requested?: number
    /** 给出加减仓动作的品种数 */
    actionCount?: number
    /** 维持持有的品种数 */
    holdCount?: number
    /** 不给动作的品种数 */
    undecidableCount?: number
    /** 建议买入合计，单位元 */
    totalAddAmount?: number
    /** 建议卖出合计，单位元 */
    totalReduceAmount?: number
    /** 当前红利持仓合计金额，单位元 */
    totalCurrentAmount?: number
    /** 参数是否可用，false 时整批不给动作 */
    paramUsable?: boolean
    /** 参数问题清单 */
    paramIssues?: string[]
    /** 基准仓位金额，单位元 */
    baseAmount?: number
    /** 保底分红金额，单位元 */
    floorAmount?: number
    /** 加仓股息率阈值，单位百分点 */
    addYield?: number
    /** 减仓股息率阈值，单位百分点 */
    reduceYield?: number
    /** 最新股息率数据日期，页面与邮件必须显示 */
    dataDate?: string
    /** 数据最旧已过天数 */
    ageDays?: number
    /** 是否存在过期数据 */
    hasStale?: boolean
    /** 与网格策略的互斥口径说明 */
    gridNote?: string
    /** 操作提示，如无红利持仓 */
    notice?: string
    /** 汇总里的动作分布说明，供邮件直接取用 */
    summary?: string[]
  }

  type DividendManualRequest = {
    /** 指数代码，如 399321 */
    indexCode?: string
    /** 数据日期，必须由使用者填真实日期，不允许默认今天 */
    dataDate?: string
    /** 股息率，单位百分点，如 4.08 表示 4.08% */
    dividendYield?: number
    /** 指数中文全称，可空 */
    indexName?: string
  }

  type DividendPlanVO = {
    /** 基准仓位金额，单位元 */
    baseAmount?: number
    /** 保底分红金额，单位元 */
    floorAmount?: number
    /** 加仓股息率阈值，单位百分点 */
    addYield?: number
    /** 减仓股息率阈值，单位百分点 */
    reduceYield?: number
    /** 参数是否齐全且自洽 */
    usable?: boolean
    /** 参数问题清单，可用时为空 */
    issues?: string[]
  }

  type DividendQueryRequest = {
    /** 指数代码 */
    indexCode?: string
    /** 基金业务主键，传入时按其跟踪指数查询 */
    varietyKey?: string
    /** 返回的最近期数，为空时取默认值 */
    limit?: number
  }

  type DividendWatchVO = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 跟踪指数代码，未登记时为 null */
    indexCode?: string
    /** 跟踪指数全称 */
    indexName?: string
    /** 当前持有金额，单位元；取不到时为 null */
    currentAmount?: number
    /** 当前股息率，单位百分点；取不到时为 null */
    dividendYield?: number
    /** 股息率数据日期 */
    dataDate?: string
    /** 股息率距今天数 */
    ageDays?: number
    /** 股息率是否已过期，过期时不给加减仓动作 */
    stale?: boolean
    /** 该标的是否能正常参与建议，false 时看 reason */
    usable?: boolean
    /** 不能参与建议的原因，逐条可读 */
    reasons?: string[]
  }

  type EstimateBatchRequest = {
    /** 品种业务主键列表，为空时按全部 HOLDING 状态的持仓预估 */
    varietyKeys?: string[]
    /** 是否跳过缓存强制重算 */
    refresh?: boolean
  }

  type EstimateBatchVO = {
    /** 可预估的基金，按预估涨跌幅降序 */
    items?: EstimateVO[]
    /** 不可预估的基金，逐只带出原因 */
    unavailableItems?: EstimateVO[]
    /** 本次预估的基金数 */
    requested?: number
    /** 可预估的基金数 */
    estimableCount?: number
    /** 不可预估的基金数 */
    unavailableCount?: number
    /** 整体预估涨跌幅，按持仓等权平均，百分点；只统计可预估且为 A 档的基金 */
    overallChange?: number
    /** 参与整体统计的基金数 */
    overallCount?: number
    /** 行情数据日期 */
    dataDate?: string
    /** 是否处于交易时段 */
    tradingHours?: boolean
    /** 是否有基金走了降级缓存 */
    degraded?: boolean
    /** 混合型基金数，页面据此提示「仅方向参考」 */
    mixedCount?: number
    /** 整体涨跌的口径说明，只统计 A 档时给出提示 */
    overallNote?: string
    /** 操作提示，如超上限已分批 */
    notice?: string
  }

  type EstimateErrorQueryRequest = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 区间起始日，含 */
    startDate?: string
    /** 区间截止日，含 */
    endDate?: string
    /** 是否超阈值 */
    overThreshold?: boolean
  }

  type EstimateErrorTrendVO = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 基金类型 */
    category?: 'STOCK' | 'MIXED' | 'BOND' | 'GOLD' | 'DIVIDEND' | 'ETF' | 'UNKNOWN'
    /** 基金类型中文名 */
    categoryLabel?: string
    /** 滚动窗口内的样本数 */
    sampleCount?: number
    /** 滚动窗口内绝对误差的中位数，百分点 */
    medianAbsError?: number
    /** 滚动窗口内的平均绝对误差，百分点 */
    mae?: number
    /** 滚动窗口内的方向准确率 */
    directionAccuracy?: number
    /** 滚动窗口内的区间覆盖率，混合型才有 */
    intervalCoverage?: number
    /** 最近一次滚动判定的档位 */
    rollingTier?: 'A' | 'B' | 'C'
    /** 最近一次校准的交易日 */
    latestTradeDate?: string
    /** 连续超阈值期数 */
    consecutiveOverCount?: number
    /** 是否建议停用该基金预估 */
    suggestSuspend?: boolean
    /** 逐日误差序列，按交易日升序 */
    series?: EstimateErrorVO[]
  }

  type EstimateErrorVO = {
    /** 主键 */
    id?: number
    /** 品种业务主键 */
    varietyKey?: string
    /** 交易日 */
    tradeDate?: string
    /** 预估涨跌幅 */
    estimatedChange?: number
    /** 实际涨跌幅 */
    actualChange?: number
    /** 误差 */
    errorValue?: number
    /** 是否超阈值 */
    overThreshold?: boolean
    /** 本次判定所用阈值，百分点；混合型不参与点估计判定，为 null */
    thresholdValue?: number
    /** 方向是否命中，混合型统计用 */
    directionHit?: boolean
    /** 实际值是否落在所给区间内，混合型统计用 */
    inInterval?: boolean
    /** 所给区间宽度，百分点 */
    intervalWidth?: number
    /** 重仓股披露期 */
    disclosurePeriod?: string
    /** 当时持仓截止日 */
    holdingCutoffDate?: string
    /** 当时持仓披露日 */
    holdingDisclosureDate?: string
    /** 当时输出档位 */
    outputTier?: string
    /** 预估来源，SNAPSHOT 取当日留存、RECOMPUTED 事后重算 */
    estimateSource?: string
    /** 创建时间 */
    createTime?: string
    /** 更新时间 */
    updateTime?: string
  }

  type EstimatePointVO = {
    /** 采样时刻 */
    time?: string
    /** 该时刻的整体预估涨跌幅，百分点；无 A 档基金时为 null */
    change?: number
    /** 该时刻的可预估只数 */
    estimableCount?: number
  }

  type EstimateTrackVO = {
    /** 行情所属交易日 */
    tradeDate?: string
    /** 采样点，按时间升序 */
    points?: EstimatePointVO[]
    /** 采样点数，少于两点画不出线 */
    pointCount?: number
    /** 轨迹说明，如「今日首次预估后才开始记录」 */
    note?: string
  }

  type EstimateVO = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 基金类型 */
    category?: 'STOCK' | 'MIXED' | 'BOND' | 'GOLD' | 'DIVIDEND' | 'ETF' | 'UNKNOWN'
    /** 基金类型中文名 */
    categoryLabel?: string
    /** 本次走的分支 */
    branch?: 'INDEX_ONLY' | 'STOCK_WEIGHTED' | 'GOLD_PROXY' | 'BOND_PROXY' | 'UNAVAILABLE'
    /** 分支中文名 */
    branchLabel?: string
    /** 输出档位 */
    tier?: 'A' | 'B' | 'C'
    /** 档位中文名 */
    tierLabel?: string
    /** 可信度 */
    confidence?: 'HIGH' | 'MEDIUM' | 'LOW'
    /** 可信度中文名 */
    confidenceLabel?: string
    /** 预估涨跌幅，百分点；仅 A 档有值，B 档与 C 档为 null */
    estimatedChange?: number
    /** 涨跌方向，B 档与部分 C 档使用 */
    direction?: 'UP' | 'DOWN' | 'FLAT'
    /** 方向中文名 */
    directionLabel?: string
    /** 区间下限，百分点，仅 B 档 */
    intervalLow?: number
    /** 区间上限，百分点，仅 B 档 */
    intervalHigh?: number
    /** 参与加权的覆盖率，百分点 */
    coverage?: number
    /** 持仓报告期 */
    holdingPeriod?: string
    /** 持仓截止日 */
    holdingCutoffDate?: string
    /** 持仓披露日 */
    holdingDisclosureDate?: string
    /** 净值数据日期 */
    netWorthDate?: string
    /** 行情数据日期 */
    quoteDate?: string
    /** 行情是否来自交易时段内 */
    realtime?: boolean
    /** 代理标的完整代码 */
    proxySymbol?: string
    /** 黄金 β */
    beta?: number
    /** 黄金 AR(1) 系数 */
    rho?: number
    /** 黄金残差 P90，百分点；供 β+AR(1) 估计的交叉校验与校准留存 */
    residualP90?: number
    /** 是否来自降级缓存 */
    degraded?: boolean
    /** 是否可预估 */
    estimable?: boolean
    /** 不可预估原因，可预估时为 null */
    unavailableReason?: string
    /** 不可预估细节，如停牌标的名单 */
    unavailableDetail?: string
    /** 同屏说明文字，每条一句 */
    remarks?: string[]
  }

  type ForexRateVO = {
    /** 汇率对，如 HKDCNY */
    pair?: string
    /** 最新汇率 */
    rate?: number
    /** 上一有效交易日的汇率 */
    previousRate?: number
    /** 当日变动，小数形式 */
    changeRate?: number
    /** 汇率数据日期 */
    tradeDate?: string
    /** 取数来源 */
    source?: string
    /** 是否可用 */
    usable?: boolean
  }

  type FundCandidateVO = {
    /** 业务主键，如 OF:000001 */
    varietyKey?: string
    /** 品种类型 */
    varietyType?: 'OF' | 'ETF' | 'IDX'
    /** 品种类型中文名 */
    varietyTypeLabel?: string
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 基金类型 */
    category?: 'STOCK' | 'MIXED' | 'BOND' | 'GOLD' | 'DIVIDEND' | 'ETF' | 'UNKNOWN'
    /** 基金类型中文名 */
    categoryLabel?: string
    /** 类型是否已确定，false 表示须使用者手动指定 */
    confirmed?: boolean
    /** 跟踪指数代码，红利基金用于查股息率 */
    trackIndexCode?: string
    /** 是否可从持仓入口录入，指数与股票为 false */
    recordable?: boolean
    /** 身份提示，说明判定依据或需要注意的地方 */
    note?: string
    /** 是否已写入品种注册表，已确认过的代码不再重复询问 */
    registered?: boolean
    /** 该品种当前的持有状态，未注册时为 null */
    holdingSummary?: HoldingSummaryVO
  }

  type FundCatalogStatusVO = {
    /** 缓存总条数 */
    total?: number
    /** 最近一次同步时间 */
    syncTime?: string
    /** 是否已过期，过期时下次消歧会触发同步 */
    stale?: boolean
    /** 允许的最大缓存天数 */
    maxAgeDays?: number
  }

  type FundCatalogSyncVO = {
    /** 同步后的总条数 */
    total?: number
    /** 同步完成时间 */
    syncTime?: string
    /** 同步耗时毫秒 */
    costMs?: number
  }

  type FundConfirmRequest = {
    /** 6 位代码 */
    code?: string
    /** 品种类型，只能是场外基金或场内 ETF */
    varietyType?: 'OF' | 'ETF' | 'IDX'
    /** 基金类型，自动判定为待确认时必填 */
    category?: 'STOCK' | 'MIXED' | 'BOND' | 'GOLD' | 'DIVIDEND' | 'ETF' | 'UNKNOWN'
    /** 跟踪指数代码，红利基金必填以取股息率 */
    trackIndexCode?: string
  }

  type FundDisambiguateRequest = {
    /** 6 位基金或在市代码 */
    code?: string
  }

  type FundDisambiguateVO = {
    /** 查询的 6 位代码 */
    code?: string
    /** 是否命中任何候选 */
    exists?: boolean
    /** 候选列表，按场外基金、场内 ETF、指数顺序 */
    candidates?: FundCandidateVO[]
    /** 名称相近的其他份额，A/C 类并列展示供核对 */
    relatedShareClasses?: FundCandidateVO[]
    /** 同码股票提示，股票不从持仓入口录入 */
    stockHint?: string
    /** 是否存在类型待确认的候选 */
    needConfirm?: boolean
    /** 是否降级，指数校验失败时为 true */
    degraded?: boolean
    /** 基金全集缓存的数据时间 */
    catalogUpdatedAt?: string
  }

  type FundSnapshotVO = {
    /** 6 位基金代码 */
    code?: string
    /** 基金简称 */
    name?: string
    /** 净值数据日期 */
    netWorthDate?: string
    /** 持仓截止日 */
    holdingCutoffDate?: string
    /** 持仓披露日 */
    holdingDisclosureDate?: string
    /** 季末仓位报告期截止日，无股票持仓时用它表示数据的报告期 */
    allocationReportDate?: string
    /** 报告期，如 2026Q2 */
    holdingPeriod?: string
    /** 重仓股列表，按权重降序 */
    stockHoldings?: HoldingItemVO[]
    /** 债券持仓列表 */
    bondHoldings?: HoldingItemVO[]
    /** 重仓股合计占比，单位百分点；季报只有前十大时明显低于股票仓位 */
    totalStockWeight?: number
    /** 债券持仓合计占比，单位百分点 */
    totalBondWeight?: number
    /** 是否全量披露 */
    fullDisclosure?: boolean
    /** 季末股票占净比 */
    stockRatio?: number
    /** 季末债券占净比 */
    bondRatio?: number
    /** 季末现金占净比 */
    cashRatio?: number
    /** 是否可用于当日预估 */
    estimable?: boolean
    /** 不可预估的具体原因，可预估时为 null */
    unavailableReason?: string
    /** 是否走了降级缓存 */
    degraded?: boolean
    /** 降级时的数据截止时间 */
    dataCutoffDate?: string
    /** 净值序列，按日期升序 */
    netWorthPoints?: NetWorthPointVO[]
  }

  type goldBetaParams = {
    varietyKey?: string
    startDate?: string
    endDate?: string
  }

  type GoldBetaRecordVO = {
    /** 主键 */
    id?: number
    /** 品种业务主键 */
    varietyKey?: string
    /** 记录日 */
    tradeDate?: string
    /** 对黄金 ETF 的滚动 β */
    beta?: number
    /** 回归截距，百分点 */
    alpha?: number
    /** AR(1) 误差系数 ρ */
    rho?: number
    /** 残差绝对值的 P90，百分点 */
    residualP90?: number
    /** 参与估计的配对样本数 */
    samples?: number
    /** 是否因样本不足退化为代理直取 */
    fallback?: boolean
    /** 写入时间 */
    createTime?: string
  }

  type GridAdviceRequest = {
    /** 品种业务主键，为空时按全部持有中的场内 ETF */
    varietyKey?: string
    /** 手工指定的当前价，留空则取行情 */
    currentPrice?: number
    /** 手工指定的参考价，留空则取上一交易日收盘价 */
    referencePrice?: number
  }

  type GridAdviceVO = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 基金类型中文名 */
    categoryLabel?: string
    /** 动作，买入、卖出、未触发或已让位 */
    action?: 'BUY' | 'SELL' | 'NONE' | 'SUPPRESSED'
    /** 动作中文名 */
    actionLabel?: string
    /** 判断所用的价格口径，场内实时价或场外尾盘预估净值 */
    basis?: 'REALTIME_PRICE' | 'ESTIMATED_NAV'
    /** 价格口径中文名 */
    basisLabel?: string
    /** 当前价，单位元 */
    currentPrice?: number
    /** 当前价的数据日期 */
    priceDate?: string
    /** 当前价的取数说明，如「行情价（实时）」 */
    priceSource?: string
    /** 参考价，判断穿越方向的基准，一般为上一交易日收盘价 */
    referencePrice?: number
    /** 参考价的数据日期 */
    referenceDate?: string
    /** 当前所处格子序号，从下界起算为 0；超出区间为 null */
    currentGrid?: number
    /** 本次越过的网格线价格，未触发为 null */
    crossedLine?: number
    /** 建议买卖金额，单位元，未触发为 null */
    suggestAmount?: number
    /** 网格线总条数 */
    lineCount?: number
    /** 网格下界 */
    lowerBound?: number
    /** 网格上界 */
    upperBound?: number
    /** 每格金额 */
    amountPerGrid?: number
    /** 是否因同标的有红利建议而让位 */
    suppressed?: boolean
    /** 判断依据文字，逐条可读 */
    remarks?: string[]
  }

  type GridAdvisoryVO = {
    /** 逐只提醒，按触发优先、再按建议金额降序 */
    items?: GridAdviceVO[]
    /** 未触发的标的，逐只带原因 */
    idleItems?: GridAdviceVO[]
    /** 本次纳入的标的数 */
    requested?: number
    /** 触发提醒的标的数 */
    triggeredCount?: number
    /** 因红利建议让位的标的数 */
    suppressedCount?: number
    /** 未触发的标的数 */
    idleCount?: number
    /** 建议买入合计，单位元 */
    totalBuyAmount?: number
    /** 建议卖出合计，单位元 */
    totalSellAmount?: number
    /** 参数是否可用，false 时整批不给提醒 */
    paramUsable?: boolean
    /** 参数问题清单 */
    paramIssues?: string[]
    /** 与红利策略的互斥口径说明 */
    dividendNote?: string
    /** 操作提示，如无场内 ETF 持仓 */
    notice?: string
    /** 汇总摘要，供邮件直接取用 */
    summary?: string[]
  }

  type GridParamSaveRequest = {
    /** 品种业务主键；留空表示保存通用参数，供尚未逐只配置的标的兜底 */
    varietyKey?: string
    /** 价格下界，单位元 */
    lowerBound?: number
    /** 价格上界，单位元 */
    upperBound?: number
    /** 相邻网格线的价格间距，单位元 */
    step?: number
    /** 每格金额，单位元 */
    amountPerGrid?: number
  }

  type GridPlanVO = {
    /** 所属品种业务主键；通用参数为 null */
    varietyKey?: string
    /** 所属品种名称，供页面显示这套格子管的是哪只 */
    varietyName?: string
    /** 价格下界，单位元 */
    lowerBound?: number
    /** 价格上界，单位元 */
    upperBound?: number
    /** 相邻网格线的价格间距，单位元 */
    step?: number
    /** 每格金额，单位元 */
    amountPerGrid?: number
    /** 网格线序列，参数可用时给出，供页面直接展示划格结果 */
    lines?: number[]
    /** 参数是否齐全且自洽 */
    usable?: boolean
    /** 参数问题清单，可用时为空 */
    issues?: string[]
    /** 参数提示，如「正在沿用通用参数」；不影响可用性 */
    notes?: string[]
  }

  type GridWatchVO = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 基金类型中文名 */
    categoryLabel?: string
    /** 品种类型，OF 场外 / ETF 场内 */
    varietyType?: string
    /** 判断所用的价格口径，场内实时价或场外尾盘预估净值 */
    basis?: 'REALTIME_PRICE' | 'ESTIMATED_NAV'
    /** 价格口径中文名 */
    basisLabel?: string
    /** 场外基金用来估净值的跟踪 ETF 或指数行情代码 */
    proxySymbol?: string
    /** 该标的的价格下界，单位元 */
    lowerBound?: number
    /** 该标的的价格上界，单位元 */
    upperBound?: number
    /** 该标的的格子大小，单位元 */
    step?: number
    /** 该标的的每格金额，单位元 */
    amountPerGrid?: number
    /** 该标的的参数是否齐全且自洽 */
    paramUsable?: boolean
    /** 参数问题清单，可用时为空 */
    paramIssues?: string[]
    /** 参数提示，如「正在沿用通用参数」 */
    paramNotes?: string[]
    /** 该标的能划出的网格线条数 */
    lineCount?: number
    /** 是否已单独配置；false 表示仍在沿用通用参数 */
    customized?: boolean
  }

  type historyParams = {
    codes: string[]
    limit?: number
  }

  type HoldingBatchUpdateRequest = {
    /** 持仓批次主键 */
    id?: number
    /** 更正后的份额，必须为正数 */
    shares?: number
    /** 更正后的成本价，必须为正数 */
    costPrice?: number
    /** 更正后的建仓日期，为空则不改 */
    openDate?: string
  }

  type HoldingBatchVO = {
    /** 持仓批次主键 */
    id?: number
    /** 持有份额 */
    shares?: number
    /** 成本价 */
    costPrice?: number
    /** 成本金额，份额乘成本价 */
    costAmount?: number
    /** 建仓日期 */
    openDate?: string
    /** 录入时间 */
    createTime?: string
  }

  type HoldingBuyRequest = {
    /** 所持品种的业务主键，如 OF:000001 */
    varietyKey?: string
    /** 买入份额，必须为正数 */
    shares?: number
    /** 买入成本价，必须为正数 */
    costPrice?: number
    /** 建仓日期，为空时取当日 */
    openDate?: string
  }

  type HoldingChangeQueryRequest = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 变动类型 */
    changeType?: 'BUY' | 'SELL'
    /** 返回条数上限，为空时取默认值 */
    limit?: number
  }

  type HoldingChangeRevokeRequest = {
    /** 持仓批次主键 */
    id?: number
  }

  type HoldingChangeVO = {
    /** 主键 */
    id?: number
    /** 所持品种的业务主键 */
    varietyKey?: string
    /** 品种名称，关联品种注册表带出，便于逐笔核对 */
    varietyName?: string
    /** 变动类型 */
    changeType?: 'BUY' | 'SELL'
    /** 变动份额，正数 */
    shares?: number
    /** 成交单价 */
    unitPrice?: number
    /** 变动后合计份额 */
    afterShares?: number
    /** 备注 */
    note?: string
    /** 业务日期，买入为建仓日期，卖出为操作当日 */
    openDate?: string
    /** 买入对应的持仓批次主键，供更正与撤销定位；卖出与历史行为空 */
    holdingId?: number
    /** 本次变动金额，份额乘单价，供明细核对 */
    amount?: number
    /** 创建时间 */
    createTime?: string
  }

  type HoldingConvertRequest = {
    /** 转出的品种业务主键 */
    fromVarietyKey?: string
    /** 转入的品种业务主键，必须已注册且类型已确认 */
    toVarietyKey?: string
    /** 转出的份额，必须为正数且不超过当前持有 */
    shares?: number
    /** 转入份额，按转入基金的实际申购份额填写 */
    toShares?: number
    /** 转入成本价，即转入基金的成交净值 */
    toCostPrice?: number
    /** 业务日期，为空时取当日 */
    openDate?: string
  }

  type HoldingConvertVO = {
    /** 持仓总览的单品种聚合行 */
    fromVariety?: HoldingVarietyVO
    /** 持仓总览的单品种聚合行 */
    toVariety?: HoldingVarietyVO
    /** 转出只数 */
    outCount?: number
    /** 转入只数 */
    inCount?: number
    /** 转换说明，如「已转出 100 份、转入 95.24 份」 */
    note?: string
  }

  type HoldingDividendVO = {
    /** 除息日，即单位净值被扣掉分红的那一天 */
    date?: string
    /** 每份现金分红金额，单位元 */
    perShare?: number
    /** 该次除息实际计入的持有份额，除息日之后才买入的批次不计入 */
    shares?: number
    /** 该次除息对应到当前持有份额的金额，单位元 */
    amount?: number
  }

  type HoldingItemVO = {
    /** 证券代码 */
    code?: string
    /** 证券名称 */
    name?: string
    /** 占净值比例，单位百分点 */
    weight?: number
  }

  type HoldingOverviewVO = {
    /** 持仓品种数，与 PRD 的 30 只上限对应 */
    varietyCount?: number
    /** 逐品种聚合行 */
    items?: HoldingVarietyVO[]
    /** 持仓市值合计，单位元；净值或行情缺失的品种按 0 计 */
    totalMarketValue?: number
    /** 当日收益合计，单位元；缺当日涨跌幅的品种不计入 */
    totalDailyProfitLoss?: number
    /** 含现金分红的总收益合计，与支付宝「持有收益」同口径 */
    totalProfitLoss?: number
    /** 成本合计，单位元 */
    totalCostAmount?: number
    /** 含分红的总收益率合计，单位百分点 */
    totalProfitRate?: number
    /** 汇总说明，如部分品种净值缺失 */
    notice?: string
  }

  type HoldingQueryRequest = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 持仓状态 */
    status?: 'HOLDING' | 'CLOSED'
  }

  type HoldingSellRequest = {
    /** 所持品种的业务主键，如 OF:000001 */
    varietyKey?: string
    /** 卖出份额，必须为正数且不超过当前持有合计 */
    shares?: number
  }

  type HoldingSummaryVO = {
    /** 是否已有持有中持仓 */
    holding?: boolean
    /** 合计持有份额，无持仓为 null */
    totalShares?: number
    /** 加权平均成本价，无持仓为 null */
    avgCostPrice?: number
  }

  type HoldingVarietyVO = {
    /** 品种业务主键 */
    varietyKey?: string
    /** 品种类型 */
    varietyType?: 'OF' | 'ETF' | 'IDX'
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 基金类型，UNKNOWN 表示待确认 */
    category?: 'STOCK' | 'MIXED' | 'BOND' | 'GOLD' | 'DIVIDEND' | 'ETF' | 'UNKNOWN'
    /** 基金类型中文名 */
    categoryLabel?: string
    /** 类型是否已确认 */
    confirmed?: boolean
    /** 合计持有份额，同一品种多条持仓合并 */
    totalShares?: number
    /** 加权平均成本价，按份额加权 */
    avgCostPrice?: number
    /** 最新单位净值，场内 ETF 用最新行情价；取不到为 null */
    latestPrice?: number
    /** 最新净值或行情的日期 */
    latestPriceDate?: string
    /** 最新价的取数说明，如「净值 2026-10-27」或「行情价（实时）」 */
    priceSource?: string
    /** 最新一条持仓记录的建仓日期 */
    lastOpenDate?: string
    /** 持仓状态，全部记录 CLOSED 时为 CLOSED */
    status?: 'HOLDING' | 'CLOSED'
    /** 持仓成本金额，份额乘加权成本价 */
    costAmount?: number
    /** 持仓市值，份额乘最新价；最新价缺失时为 null */
    marketValue?: number
    /** 浮盈亏，市值减成本金额；最新价缺失时为 null */
    profitLoss?: number
    /** 浮盈亏比例，单位百分点；最新价缺失时为 null */
    profitRate?: number
    /** 含现金分红的总收益，与支付宝「持有收益」同口径；最新价缺失时为 null */
    totalProfitLoss?: number
    /** 含现金分红的总收益率，单位百分点；最新价缺失时为 null */
    totalProfitRate?: number
    /** 当日涨跌幅，单位百分点；没有可用日变动时为 null */
    dailyChangePercent?: number
    /** 当日收益金额，单位元；按当日涨跌幅反推昨日市值后相减，缺日变动时为 null */
    dailyProfitLoss?: number
    /** 持仓期间累计的现金分红金额，单位元；无累计净值数据时为 null */
    dividendAmount?: number
    /** 分红口径说明，如「按累计净值推算，建仓以来」 */
    dividendNote?: string
    /** 分红方式，红利再投时现金分红为 0，分红已折成份额 */
    dividendMethod?: 'CASH' | 'REINVEST'
    /** 逐笔除息明细，页面据此与平台核对，不参与任何金额计算 */
    dividendDetails?: HoldingDividendVO[]
    /** 持有中的批次，供页面更正份额、成本价与建仓日期 */
    batches?: HoldingBatchVO[]
  }

  type HoldingVO = {
    /** 主键 */
    id?: number
    /** 所持品种的业务主键 */
    varietyKey?: string
    /** 持有份额 */
    shares?: number
    /** 成本价 */
    costPrice?: number
    /** 建仓日期 */
    openDate?: string
    /** 持仓状态 */
    status?: 'HOLDING' | 'CLOSED'
    /** 创建时间 */
    createTime?: string
    /** 更新时间 */
    updateTime?: string
  }

  type IndexDividendOverviewVO = {
    /** 指数代码 */
    indexCode?: string
    /** 指数中文全称 */
    indexName?: string
    /** 最新数据日期，页面与邮件必须显示 */
    dataDate?: string
    /** 最新股息率，单位百分点 */
    dividendYield?: number
    /** 数据来源，取最新一期 */
    sourceLabel?: string
    /** 距今已过天数 */
    ageDays?: number
    /** 是否已过期，过期时不得据此给出加减仓动作 */
    stale?: boolean
    /** 过期说明，未过期时为 null */
    staleReason?: string
    /** 自动抓取是否可用，国证系列为 false */
    autoAvailable?: boolean
    /** 关联的基金代码列表，即跟踪该指数的持仓 */
    fundCodes?: string[]
    /** 近期序列，按日期降序 */
    history?: IndexDividendVO[]
  }

  type IndexDividendRefreshVO = {
    /** 刷新日期 */
    refreshDate?: string
    /** 成功写入的指数数量 */
    savedCount?: number
    /** 自动抓取不可用、需要手工覆盖的指数数量 */
    manualRequiredCount?: number
    /** 明细，逐只带结果与原因 */
    items?: Item[]
  }

  type IndexDividendVO = {
    /** 指数代码 */
    indexCode?: string
    /** 指数中文全称 */
    indexName?: string
    /** 数据日期 */
    dataDate?: string
    /** 股息率，单位百分点 */
    dividendYield?: number
    /** 股息率1（总股本） */
    dividendYield1?: number
    /** 股息率2（计算用股本） */
    dividendYield2?: number
    /** 数据来源 */
    source?: 'AUTO' | 'MANUAL'
    /** 数据来源中文名 */
    sourceLabel?: string
  }

  type IndicatorAdvisoryVO = {
    /** 逐只指标结果 */
    items?: IndicatorVO[]
    /** 无法计算的标的，逐只带原因 */
    unavailableItems?: IndicatorVO[]
    /** 本次纳入的标的数 */
    requested?: number
    /** 算出指标的标的数 */
    completeCount?: number
    /** 无法计算的标的数 */
    unavailableCount?: number
    /** 触发信号条数 */
    signalCount?: number
    /** 参数是否可用，false 时整批不算指标 */
    paramUsable?: boolean
    /** 参数问题清单 */
    paramIssues?: string[]
    /** 本次生效的参数摘要 */
    paramSummary?: string
    /** 指标信号是否参与收盘前邮件 */
    participateInAlert?: boolean
    /** 逐条摘要，供邮件直接取用 */
    summary?: string[]
    /** 操作提示，如无持仓 */
    notice?: string
  }

  type IndicatorQueryRequest = {
    /** 裸代码或带前缀代码，与 varietyKey 二选一 */
    code?: string
    /** 品种业务主键，与 code 二选一 */
    varietyKey?: string
    /** 返回的日线根数，小于等于 0 时按参数需要自动取 */
    limit?: number
  }

  type IndicatorVO = {
    /** 带交易所前缀的完整代码 */
    symbol?: string
    /** 品种业务主键，按品种查询时有值 */
    varietyKey?: string
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** RSI 当前值 */
    rsi?: number
    /** KDJ 的 K 值 */
    k?: number
    /** KDJ 的 D 值 */
    d?: number
    /** KDJ 的 J 值 */
    j?: number
    /** MACD 快慢线差值 DIF */
    dif?: number
    /** MACD 信号线 DEA */
    dea?: number
    /** MACD 柱，按 (DIF−DEA)×2 口径 */
    macdBar?: number
    /** 触发的信号 */
    signals?: (
      | 'RSI_OVERBOUGHT'
      | 'RSI_OVERSOLD'
      | 'MACD_GOLDEN_CROSS'
      | 'MACD_DEAD_CROSS'
      | 'KDJ_GOLDEN_CROSS'
      | 'KDJ_DEAD_CROSS'
    )[]
    /** 信号中文名，逐条可读 */
    signalLabels?: string[]
    /** 本次计算所用参数的中文摘要，页面必须与之一致 */
    paramSummary?: string
    /** 最新日线日期 */
    barDate?: string
    /** 参与计算的日线根数 */
    samples?: number
    /** 日线是否前复权 */
    adjusted?: boolean
    /** 日线取数来源 */
    source?: string
    /** 是否算出全部三个指标 */
    complete?: boolean
    /** 是否命中缓存 */
    cached?: boolean
    /** 计算说明，逐条带具体数值 */
    remarks?: string[]
    /** 数据不足或无法计算的原因 */
    unavailableReason?: string
  }

  type Item = {
    /** 指数代码 */
    indexCode?: string
    /** 指数中文全称 */
    indexName?: string
    /** 数据日期 */
    dataDate?: string
    /** 股息率，单位百分点 */
    dividendYield?: number
    /** 是否写入成功 */
    saved?: boolean
    /** 是否需要手工覆盖，自动抓取取不到时为 true */
    manualRequired?: boolean
    /** 结果说明，失败与跳过时给出原因 */
    message?: string
  }

  type LoginRequest = {
    /** 用户名 */
    username?: string
    /** 口令，明文传输但只在 HTTPS 下，服务端只与密钥文件比对，不落库不记日志 */
    password?: string
  }

  type MailPreviewVO = {
    /** 邮件主题 */
    subject?: string
    /** HTML 正文 */
    html?: string
    /** 纯文本兜底正文 */
    text?: string
  }

  type MailSettingSaveRequest = {
    /** 收件地址，多个用逗号分隔 */
    recipients?: string
    /** SMTP 主机 */
    host?: string
    /** SMTP 端口，465 走 SSL、587 走 STARTTLS */
    port?: string
    /** SMTP 账号 */
    username?: string
    /** 发件地址，留空时取账号 */
    from?: string
    /** 授权码，留空表示不改；写入后任何接口都不回显 */
    password?: string
    /** 是否清除已保存的授权码，为 true 时忽略 password */
    clearPassword?: boolean
  }

  type MailSettingVO = {
    /** 收件地址，多个用逗号分隔 */
    recipients?: string
    /** SMTP 主机 */
    host?: string
    /** SMTP 端口 */
    port?: string
    /** SMTP 账号 */
    username?: string
    /** 发件地址 */
    from?: string
    /** 授权码是否已配置，只回布尔值 */
    passwordConfigured?: boolean
    /** 通道是否具备发送条件 */
    ready?: boolean
    /** 状态说明，未就绪时逐项列出缺什么 */
    note?: string
    /** 当前配置是否可以进行连接测试 */
    testable?: boolean
    /** 最近一次连接测试结果 */
    testNote?: string
  }

  type MailStatusVO = {
    /** 是否具备发送条件 */
    ready?: boolean
    /** 状态说明，未就绪时逐项列出缺什么 */
    note?: string
    /** 当前配置是否可以进行连接测试 */
    testable?: boolean
  }

  type MailTestVO = {
    /** 连接测试是否成功 */
    ok?: boolean
    /** 测试结果说明 */
    message?: string
  }

  type MapString = {
    key?: string
  }

  type MarketBarVO = {
    /** 交易日 */
    date?: string
    /** 收盘价 */
    close?: number
    /** 开盘价，上游未提供时为 null */
    open?: number
    /** 最高价，KDJ 的 RSV 必须用它 */
    high?: number
    /** 最低价，KDJ 的 RSV 必须用它 */
    low?: number
    /** 成交量，上游未提供时为 null */
    volume?: number
  }

  type MarketHistoryVO = {
    /** 带交易所前缀的完整代码 */
    symbol?: string
    /** 日线，按日期升序 */
    bars?: MarketBarVO[]
    /** 根数 */
    size?: number
    /** 最新交易日 */
    latestDate?: string
    /** 最新收盘价 */
    latestClose?: number
    /** 是否前复权口径 */
    adjusted?: boolean
    /** 取数来源 */
    source?: string
  }

  type MarketQuoteVO = {
    /** 带交易所前缀的完整代码 */
    symbol?: string
    /** 标的名称 */
    name?: string
    /** 最新价 */
    price?: number
    /** 昨收 */
    previousClose?: number
    /** 当日涨跌幅，单位百分点 */
    changePercent?: number
    /** 数据日期 */
    tradeDate?: string
    /** 行情时间 */
    quoteTime?: string
    /** 是否交易时段内的实时数据，false 表示上一交易日 */
    realtime?: boolean
    /** 可用性状态 */
    status?: 'OK' | 'SUSPENDED' | 'NO_DATA' | 'UNAVAILABLE'
    /** 是否可用于预估 */
    usable?: boolean
    /** 不可用原因 */
    unavailableReason?: string
    /** 取数来源 */
    source?: string
    /** 是否前复权口径 */
    adjusted?: boolean
  }

  type NetWorthPointVO = {
    /** 净值日期 */
    netWorthDate?: string
    /** 单位净值 */
    unitNetWorth?: number
    /** 当日净值回报，单位百分点 */
    equityReturn?: number
  }

  type overviewParams = {
    tradeDate?: string
  }

  type ParamQueryRequest = {
    /** 参数分组 */
    paramGroup?: 'DIVIDEND' | 'GRID' | 'BOND' | 'INDICATOR' | 'ALERT'
    /** 参数名 */
    paramKey?: string
  }

  type ParamSaveRequest = {
    /** 参数分组 */
    paramGroup?: 'DIVIDEND' | 'GRID' | 'BOND' | 'INDICATOR' | 'ALERT'
    /** 参数名，组内唯一 */
    paramKey?: string
    /** 参数值 */
    paramValue?: string
    /** 参数说明 */
    paramDesc?: string
  }

  type planOfParams = {
    varietyKey: string
  }

  type PointOutcome = {
    /** 产出是否成功 */
    success?: boolean
    /** 产出摘要，成功时写进页面提示 */
    detail?: string
    /** 内容留存路径，邮件等有正文的时点填写，相对 data 目录 */
    contentPath?: string
    /** 产出失败的说明 */
    failReason?: string
  }

  type previewParams = {
    tradeDate?: string
  }

  type QuoteBatchRequest = {
    /** 标的代码列表，可带 sh/sz/bj/hk 前缀，不带时按号段推断 */
    codes?: string[]
    /** 日线根数，只在查历史时使用，不传取配置默认值 */
    historyLimit?: number
  }

  type QuoteBatchVO = {
    /** 逐只行情，按请求顺序 */
    quotes?: MarketQuoteVO[]
    /** 请求的标的数 */
    requested?: number
    /** 可用于预估的标的数 */
    usableCount?: number
    /** 停牌的标的数 */
    suspendedCount?: number
    /** 无行情的标的数，含 920xxx 北交所 */
    noDataCount?: number
    /** 本次是否有标的走了备用源 */
    fallbackUsed?: boolean
    /** 数据日期，取可用标的中最新的交易日 */
    dataDate?: string
    /** 是否处于交易时段，false 时数据为上一交易日 */
    tradingHours?: boolean
  }

  type resendParams = {
    tradeDate?: string
  }

  type resetParams = {
    group: string
  }

  type ResultAuthStatusVO = {
    code?: number
    msg?: string
    data?: AuthStatusVO
  }

  type ResultBondAdvisoryVO = {
    code?: number
    msg?: string
    data?: BondAdvisoryVO
  }

  type ResultBondMonitorVO = {
    code?: number
    msg?: string
    /** 单只债券基金的趋势监控结果，横盘天数与趋势指标必须给出具体数值 */
    data?: BondMonitorVO
  }

  type ResultBondPlanVO = {
    code?: number
    msg?: string
    data?: BondPlanVO
  }

  type ResultBoolean = {
    code?: number
    msg?: string
    data?: boolean
  }

  type ResultCalibrationResultVO = {
    code?: number
    msg?: string
    data?: CalibrationResultVO
  }

  type ResultDividendAdviceVO = {
    code?: number
    msg?: string
    /** 单只红利品种的加减仓建议，金额已取整到元并带数据日期 */
    data?: DividendAdviceVO
  }

  type ResultDividendAdvisoryVO = {
    code?: number
    msg?: string
    data?: DividendAdvisoryVO
  }

  type ResultDividendPlanVO = {
    code?: number
    msg?: string
    data?: DividendPlanVO
  }

  type ResultEstimateBatchVO = {
    code?: number
    msg?: string
    data?: EstimateBatchVO
  }

  type ResultEstimateErrorTrendVO = {
    code?: number
    msg?: string
    data?: EstimateErrorTrendVO
  }

  type ResultEstimateTrackVO = {
    code?: number
    msg?: string
    data?: EstimateTrackVO
  }

  type ResultForexRateVO = {
    code?: number
    msg?: string
    data?: ForexRateVO
  }

  type ResultFundCatalogStatusVO = {
    code?: number
    msg?: string
    data?: FundCatalogStatusVO
  }

  type ResultFundCatalogSyncVO = {
    code?: number
    msg?: string
    data?: FundCatalogSyncVO
  }

  type ResultFundDisambiguateVO = {
    code?: number
    msg?: string
    data?: FundDisambiguateVO
  }

  type ResultFundSnapshotVO = {
    code?: number
    msg?: string
    data?: FundSnapshotVO
  }

  type ResultGridAdviceVO = {
    code?: number
    msg?: string
    /** 单只标的的网格到价提醒，含当前格子、穿越的网格线与建议金额 */
    data?: GridAdviceVO
  }

  type ResultGridAdvisoryVO = {
    code?: number
    msg?: string
    data?: GridAdvisoryVO
  }

  type ResultGridPlanVO = {
    code?: number
    msg?: string
    data?: GridPlanVO
  }

  type ResultHoldingConvertVO = {
    code?: number
    msg?: string
    data?: HoldingConvertVO
  }

  type ResultHoldingOverviewVO = {
    code?: number
    msg?: string
    data?: HoldingOverviewVO
  }

  type ResultHoldingSummaryVO = {
    code?: number
    msg?: string
    /** 该品种当前的持有状态，未注册时为 null */
    data?: HoldingSummaryVO
  }

  type ResultHoldingVO = {
    code?: number
    msg?: string
    /** 持仓视图对象 */
    data?: HoldingVO
  }

  type ResultIndexDividendOverviewVO = {
    code?: number
    msg?: string
    /** 指数股息率总览，把最新值与数据日期、是否过期一起返回 */
    data?: IndexDividendOverviewVO
  }

  type ResultIndexDividendRefreshVO = {
    code?: number
    msg?: string
    data?: IndexDividendRefreshVO
  }

  type ResultIndicatorAdvisoryVO = {
    code?: number
    msg?: string
    data?: IndicatorAdvisoryVO
  }

  type ResultIndicatorVO = {
    code?: number
    msg?: string
    data?: IndicatorVO
  }

  type ResultInteger = {
    code?: number
    msg?: string
    data?: number
  }

  type ResultListBigDecimal = {
    code?: number
    msg?: string
    data?: number[]
  }

  type ResultListDividendWatchVO = {
    code?: number
    msg?: string
    data?: DividendWatchVO[]
  }

  type ResultListEstimateErrorTrendVO = {
    code?: number
    msg?: string
    data?: EstimateErrorTrendVO[]
  }

  type ResultListEstimateErrorVO = {
    code?: number
    msg?: string
    data?: EstimateErrorVO[]
  }

  type ResultListGoldBetaRecordVO = {
    code?: number
    msg?: string
    data?: GoldBetaRecordVO[]
  }

  type ResultListGridWatchVO = {
    code?: number
    msg?: string
    data?: GridWatchVO[]
  }

  type ResultListHoldingChangeVO = {
    code?: number
    msg?: string
    data?: HoldingChangeVO[]
  }

  type ResultListHoldingVO = {
    code?: number
    msg?: string
    data?: HoldingVO[]
  }

  type ResultListIndexDividendOverviewVO = {
    code?: number
    msg?: string
    data?: IndexDividendOverviewVO[]
  }

  type ResultListMarketHistoryVO = {
    code?: number
    msg?: string
    data?: MarketHistoryVO[]
  }

  type ResultListSendRecordVO = {
    code?: number
    msg?: string
    data?: SendRecordVO[]
  }

  type ResultListStrategyParamVO = {
    code?: number
    msg?: string
    data?: StrategyParamVO[]
  }

  type ResultListVarietyVO = {
    code?: number
    msg?: string
    data?: VarietyVO[]
  }

  type ResultMailPreviewVO = {
    code?: number
    msg?: string
    data?: MailPreviewVO
  }

  type ResultMailSettingVO = {
    code?: number
    msg?: string
    data?: MailSettingVO
  }

  type ResultMailStatusVO = {
    code?: number
    msg?: string
    data?: MailStatusVO
  }

  type ResultMailTestVO = {
    code?: number
    msg?: string
    data?: MailTestVO
  }

  type ResultPointOutcome = {
    code?: number
    msg?: string
    data?: PointOutcome
  }

  type ResultQuoteBatchVO = {
    code?: number
    msg?: string
    data?: QuoteBatchVO
  }

  type ResultSettingOverviewVO = {
    code?: number
    msg?: string
    data?: SettingOverviewVO
  }

  type ResultSettingSaveVO = {
    code?: number
    msg?: string
    data?: SettingSaveVO
  }

  type ResultString = {
    code?: number
    msg?: string
    data?: string
  }

  type ResultTaskOverviewVO = {
    code?: number
    msg?: string
    data?: TaskOverviewVO
  }

  type ResultVarietyVO = {
    code?: number
    msg?: string
    data?: VarietyVO
  }

  type ResultVoid = {
    code?: number
    msg?: string
    data?: null
  }

  type SendRecordQueryRequest = {
    /** 发送日期 */
    sendDate?: string
    /** 发送时点 */
    sendPoint?: 'MORNING' | 'NOON' | 'PRE_CLOSE' | 'EVENING'
    /** 区间起始日，含 */
    startDate?: string
    /** 区间截止日，含 */
    endDate?: string
  }

  type SendRecordVO = {
    /** 主键 */
    id?: number
    /** 发送日期 */
    sendDate?: string
    /** 发送时点 */
    sendPoint?: 'MORNING' | 'NOON' | 'PRE_CLOSE' | 'EVENING'
    /** 发送状态 */
    status?: 'PENDING' | 'SENT' | 'FAILED' | 'RESENT'
    /** 实际发送时间 */
    sentTime?: string
    /** 失败原因 */
    failReason?: string
    /** 邮件内容留存路径 */
    contentPath?: string
    /** 创建时间 */
    createTime?: string
    /** 更新时间 */
    updateTime?: string
  }

  type SettingBatchSaveRequest = {
    /** 待保存的分组列表 */
    groups?: SettingGroupSaveRequest[]
  }

  type SettingFieldError = {
    /** 分组，与 ParamGroup 同名 */
    group?: string
    /** 字段名，与参数键同名 */
    field?: string
    /** 字段中文名，用于拼提示语 */
    label?: string
    /** 错误原因，直接展示给使用者 */
    message?: string
  }

  type SettingGroupSaveRequest = {
    /** 分组，与 ParamGroup 同名 */
    group?: string
    /** 参数键到参数值的映射，未出现的键保持原值不动 */
    values?: MapString
  }

  type SettingGroupVO = {
    /** 分组，与 ParamGroup 同名 */
    group?: string
    /** 分组中文名 */
    label?: string
    /** 分组说明，一句话 */
    description?: string
    /** 分组内的字段 */
    items?: SettingItemVO[]
  }

  type SettingItemVO = {
    /** 分组，与 ParamGroup 同名 */
    group?: string
    /** 参数键 */
    key?: string
    /** 字段中文名 */
    label?: string
    /** 字段说明，一句话 */
    description?: string
    /** 当前值，库里没有时取默认值，都没有则为 null */
    value?: string
    /** 内置默认值，用于「恢复默认」与提示 */
    defaultValue?: string
    /** 单位，如 元 / % / 日 */
    unit?: string
    /** 控件类型，TEXT / DECIMAL / INTEGER / BOOLEAN */
    type?: string
    /** 当前值是否来自库，false 表示仍在用默认值 */
    customized?: boolean
  }

  type SettingOverviewVO = {
    /** 参数分组，按固定顺序排列 */
    groups?: SettingGroupVO[]
    mailStatus?: MailStatusVO
    /** 生效说明，告诉使用者改完何时能用上 */
    effectiveNote?: string
    /** 当前是否存在校验问题，存在时页面可直接提示 */
    hasIssues?: boolean
  }

  type SettingSaveVO = {
    /** 是否保存成功 */
    ok?: boolean
    /** 成功时写入的参数条数 */
    savedCount?: number
    /** 失败时的字段级错误，页面据此逐项标红 */
    errors?: SettingFieldError[]
    /** 汇总说明，成功与失败都有一句，便于底部保存条展示 */
    message?: string
    /** 本次保存后新生效的时间点说明，如「下一个时点 20:00 生效」 */
    effectiveNote?: string
  }

  type singleParams = {
    code: string
    limit?: number
  }

  type snapshotParams = {
    code: string
  }

  type StrategyParamVO = {
    /** 主键 */
    id?: number
    /** 参数分组 */
    paramGroup?: 'DIVIDEND' | 'GRID' | 'BOND' | 'INDICATOR' | 'ALERT'
    /** 参数名 */
    paramKey?: string
    /** 参数值 */
    paramValue?: string
    /** 参数说明 */
    paramDesc?: string
    /** 创建时间 */
    createTime?: string
    /** 更新时间 */
    updateTime?: string
  }

  type summaryParams = {
    varietyKey: string
  }

  type suspendHintsParams = {
    tradeDate?: string
  }

  type TaskOverviewVO = {
    /** 统计日期 */
    tradeDate?: string
    /** 是否交易日 */
    tradingDay?: boolean
    /** 非交易日原因，交易日为空 */
    tradingDayNote?: string
    /** 逐时点状态，按应发时刻排序 */
    items?: TaskPointStatusVO[]
    /** 已成功（含已补发）的时点数 */
    sentCount?: number
    /** 失败的时点数 */
    failedCount?: number
    /** 已过应发时刻仍未成功的时点数 */
    overdueCount?: number
    /** 是否刚检测到时钟跳变 */
    clockJump?: boolean
    /** 最近一次跳变的偏差秒数 */
    clockJumpSeconds?: number
    /** 整体提示，可直接显示在页面顶部 */
    notice?: string
  }

  type TaskPointStatusVO = {
    /** 时点 */
    point?: 'MORNING' | 'NOON' | 'PRE_CLOSE' | 'EVENING'
    /** 时点中文名 */
    pointLabel?: string
    /** 应发时刻 */
    dueTime?: string
    /** 留痕状态，无记录时为空 */
    status?: 'PENDING' | 'SENT' | 'FAILED' | 'RESENT'
    /** 状态中文名，无记录时显示未发送 */
    statusLabel?: string
    /** 实际发送时间 */
    sentTime?: string
    /** 失败原因 */
    failReason?: string
    /** 邮件内容留存路径 */
    contentPath?: string
    /** 是否已过应发时刻仍未成功 */
    overdue?: boolean
    /** 一句话说明，含应发时刻与状态 */
    summary?: string
  }

  type trackParams = {
    tradeDate?: string
  }

  type trendParams = {
    varietyKey: string
    days?: number
  }

  type triggerParams = {
    /**  MORNING :早盘，约 9:30
NOON :午盘，约 11:30 至 13:00
PRE_CLOSE :收盘前 30 分钟，约 14:30
EVENING :晚间净值校准，约 20:00 至 22:00 */
    point: string
    tradeDate?: string
    resend?: boolean
  }

  type VarietyQueryRequest = {
    /** 业务主键 */
    varietyKey?: string
    /** 6 位代码 */
    varietyCode?: string
    /** 品种类型 */
    varietyType?: 'OF' | 'ETF' | 'IDX'
    /** 基金类型 */
    category?: 'STOCK' | 'MIXED' | 'BOND' | 'GOLD' | 'DIVIDEND' | 'ETF' | 'UNKNOWN'
    /** 是否已确认类型 */
    confirmed?: boolean
  }

  type VarietySaveRequest = {
    /** 业务主键，形如 OF:000001 */
    varietyKey?: string
    /** 代码所属品种类型 */
    varietyType?: 'OF' | 'ETF' | 'IDX'
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 基金类型 */
    category?: 'STOCK' | 'MIXED' | 'BOND' | 'GOLD' | 'DIVIDEND' | 'ETF' | 'UNKNOWN'
    /** 跟踪指数代码 */
    trackIndexCode?: string
    /** 分红方式，红利再投时现金分红按 0 计 */
    dividendMethod?: 'CASH' | 'REINVEST'
    /** 类型是否已确认 */
    confirmed?: boolean
  }

  type VarietyVO = {
    /** 主键 */
    id?: number
    /** 业务主键 */
    varietyKey?: string
    /** 品种类型 */
    varietyType?: 'OF' | 'ETF' | 'IDX'
    /** 6 位代码 */
    varietyCode?: string
    /** 标的名称 */
    varietyName?: string
    /** 基金类型 */
    category?: 'STOCK' | 'MIXED' | 'BOND' | 'GOLD' | 'DIVIDEND' | 'ETF' | 'UNKNOWN'
    /** 跟踪指数代码 */
    trackIndexCode?: string
    /** 分红方式，红利再投时现金分红按 0 计 */
    dividendMethod?: 'CASH' | 'REINVEST'
    /** 类型是否已确认 */
    confirmed?: boolean
    /** 创建时间 */
    createTime?: string
    /** 更新时间 */
    updateTime?: string
  }
}
