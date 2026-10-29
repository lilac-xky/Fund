/**
 * 总览页数据编排：预估、持仓、指数、日线走势与指标参数各拉一路，任一失败不影响其余
 */
import { batch as estimateBatch } from '@/api/estimateController'
import { catalogStatus as catalogStatusApi } from '@/api/fundController'
import { overview as holdingOverview } from '@/api/holdingController'
import { batch as quoteBatch, history as quoteHistory } from '@/api/marketQuoteController'
import { list as paramList } from '@/api/strategyParamController'
import { RequestError } from '@/request'
import { DEFAULT_INDICATOR_PARAMS, lastMa, macd, rsi, sparklinePoints } from './indicators'
import type { IndicatorParams } from './indicators'

/** 行情条展示的宽基指数，代码带显式交易所前缀，避免 000 段被推断成深市个股 */
const PULSE_INDEXES: Array<{ name: string; symbol: string }> = [
  { name: '上证', symbol: 'sh000001' },
  { name: '沪深300', symbol: 'sh000300' },
  { name: '中证500', symbol: 'sh000905' },
  { name: '创业板', symbol: 'sz399006' },
]

/** 午盘页取日线的根数，够算 MA20 与 MACD 即可 */
const HISTORY_LIMIT = 60

/** 一条指数行情，供行情条使用 */
export interface IndexPulse {
  /** 指数名 */
  name: string
  /** 带前缀完整代码 */
  symbol: string
  /** 当日涨跌幅，百分点 */
  change: number | null
  /** 数据日期 */
  tradeDate?: string
}

/** 一路数据的失败记录，页面据此说明「哪个环节、什么时候」出错 */
export interface LoadFailure {
  /** 出错环节 */
  stage: string
  /** 原因 */
  message: string
  /** 发生时间，ISO 字符串 */
  happenedAt: string
}

/** 一只持仓相关指数的走势与指标状态 */
export interface TrendItem {
  /** 带前缀完整代码 */
  symbol: string
  /** 标的名称 */
  name: string
  /** 最新收盘价 */
  latestClose: number | null
  /** 最近交易日 */
  latestDate?: string
  /** 当日涨跌幅，百分点 */
  change: number | null
  /** 缩略图坐标串 */
  points: string
  /** MA20 */
  ma20: number | null
  /** RSI */
  rsi: number | null
  /** MACD 柱 */
  macdBar: number | null
  /** DIF */
  dif: number | null
  /** DEA */
  dea: number | null
  /** 均线位置的一句话说明 */
  maNote: string
}

/** 整页数据 */
export interface OverviewPayload {
  /** 预估结果 */
  estimate: API.EstimateBatchVO
  /** 持仓总览，用于补市值与成本口径 */
  holding: API.HoldingOverviewVO
  /** 基金全集状态 */
  catalog: API.FundCatalogStatusVO
  /** 行情条指数 */
  indexes: IndexPulse[]
  /** 指标参数 */
  indicatorParams: IndicatorParams
  /** 持仓相关指数走势 */
  trends: TrendItem[]
  /** 本次失败的环节，全部成功时为空数组 */
  failures: LoadFailure[]
}

/** 从错误对象里取出可读文案，非请求错误按未知处理 */
function messageOf(error: unknown): string {
  if (error instanceof RequestError) {
    return `${error.message}（${error.happenedAt}）`
  }
  return error instanceof Error ? error.message : '未知错误'
}

/** 记录一次失败，环节名与时间都留在页面上 */
function failureOf(stage: string, error: unknown): LoadFailure {
  return { stage, message: messageOf(error), happenedAt: new Date().toISOString() }
} /** 读指标参数，库里没有的键沿用内置默认值 */
async function loadIndicatorParams(): Promise<IndicatorParams> {
  const result = await paramList({ paramGroup: 'INDICATOR' })
  const merged: IndicatorParams = { ...DEFAULT_INDICATOR_PARAMS }
  ;(result.data ?? []).forEach((item) => {
    const value = Number(item.paramValue)
    if (!Number.isFinite(value)) {
      return
    }
    if (item.paramKey === 'rsiPeriod') {
      merged.rsiPeriod = value
    } else if (item.paramKey === 'macdFast') {
      merged.macdFast = value
    } else if (item.paramKey === 'macdSlow') {
      merged.macdSlow = value
    } else if (item.paramKey === 'macdSignal') {
      merged.macdSignal = value
    }
  })
  return merged
}

/** 取宽基指数行情 */
async function loadIndexes(): Promise<IndexPulse[]> {
  const result = await quoteBatch({ codes: PULSE_INDEXES.map((item) => item.symbol) })
  const quotes = result.data?.quotes ?? []
  return PULSE_INDEXES.map((item) => {
    const quote = quotes.find((row) => row.symbol === item.symbol)
    return {
      name: item.name,
      symbol: item.symbol,
      change: quote?.usable === false ? null : (quote?.changePercent ?? null),
      tradeDate: quote?.tradeDate,
    }
  })
}

/** 组装单条走势的指标，取不到的字段留 null 而不是填 0 */
function buildTrend(
  symbol: string,
  name: string,
  history: API.MarketHistoryVO,
  closes: number[],
  params: IndicatorParams,
): TrendItem {
  const latest = closes[closes.length - 1] ?? null
  const previous = closes[closes.length - 2] ?? null
  const change =
    latest !== null && previous !== null && previous !== 0
      ? ((latest - previous) / previous) * 100
      : null
  const ma20 = lastMa(closes, 20)
  const macdValue = macd(closes, params.macdFast, params.macdSlow, params.macdSignal)
  let maNote = '日线样本不足，暂不算均线'
  if (latest !== null && ma20 !== null) {
    maNote = latest >= ma20 ? '收在 20 日均线之上，短期偏强' : '收在 20 日均线之下，短期偏弱'
  }
  return {
    symbol,
    name,
    latestClose: latest,
    latestDate: history.latestDate,
    change,
    points: sparklinePoints(closes),
    ma20,
    rsi: rsi(closes, params.rsiPeriod),
    macdBar: macdValue?.bar ?? null,
    dif: macdValue?.dif ?? null,
    dea: macdValue?.dea ?? null,
    maNote,
  }
}

/** 取持仓相关指数的日线，代码去重后一次批量拉取 */
async function loadTrends(
  symbols: string[],
  params: IndicatorParams,
  names: Map<string, string>,
): Promise<TrendItem[]> {
  if (symbols.length === 0) {
    return []
  }
  const result = await quoteHistory({ codes: symbols, limit: HISTORY_LIMIT })
  const items: TrendItem[] = []
  ;(result.data ?? []).forEach((history) => {
    const symbol = history.symbol ?? ''
    const closes = (history.bars ?? [])
      .map((bar) => Number(bar.close))
      .filter((value) => Number.isFinite(value) && value > 0)
    if (!symbol || closes.length < 2) {
      return
    }
    items.push(buildTrend(symbol, names.get(symbol) ?? symbol, history, closes, params))
  })
  return items
}

/**
 * 拉取整页数据，逐路失败各自记录，不因单路失败丢掉已经取回的部分
 *
 * @param refresh 是否跳过后端缓存强制重算预估
 */
export async function loadOverview(refresh = false): Promise<OverviewPayload> {
  const failures: LoadFailure[] = []
  const [estimateResult, holdingResult, catalogResult, indexResult, paramResult] =
    await Promise.allSettled([
      estimateBatch({ refresh }),
      holdingOverview(),
      catalogStatusApi(),
      loadIndexes(),
      loadIndicatorParams(),
    ])

  const estimate = estimateResult.status === 'fulfilled' ? (estimateResult.value.data ?? {}) : {}
  const holding = holdingResult.status === 'fulfilled' ? (holdingResult.value.data ?? {}) : {}
  // 尚无持仓时预估接口必然报「请先录入持仓」，这是空状态而不是故障，不列进失败环节
  // 只有持仓总览成功且确认为 0 只时才这样判定，否则两边都没取到会把真实故障吞掉
  const noHoldings = holdingResult.status === 'fulfilled' && (holding.varietyCount ?? 0) === 0
  if (estimateResult.status === 'rejected' && !noHoldings) {
    failures.push(failureOf('拉取盘中预估', estimateResult.reason))
  }
  if (holdingResult.status === 'rejected') {
    failures.push(failureOf('拉取持仓总览', holdingResult.reason))
  }
  const catalog = catalogResult.status === 'fulfilled' ? (catalogResult.value.data ?? {}) : {}
  if (catalogResult.status === 'rejected') {
    failures.push(failureOf('拉取基金全集状态', catalogResult.reason))
  }
  const indexes = indexResult.status === 'fulfilled' ? indexResult.value : []
  if (indexResult.status === 'rejected') {
    failures.push(failureOf('拉取指数行情', indexResult.reason))
  }
  const indicatorParams =
    paramResult.status === 'fulfilled' ? paramResult.value : DEFAULT_INDICATOR_PARAMS
  if (paramResult.status === 'rejected') {
    failures.push(failureOf('拉取指标参数', paramResult.reason))
  }

  // 代理标的正是这只基金对标的指数，拿它取日线就不必再猜跟踪标的
  const nameBySymbol = new Map<string, string>()
  const symbols: string[] = []
  ;[...(estimate.items ?? []), ...(estimate.unavailableItems ?? [])].forEach((item) => {
    const symbol = item.proxySymbol
    if (!symbol || nameBySymbol.has(symbol)) {
      return
    }
    nameBySymbol.set(symbol, item.varietyName ?? symbol)
    symbols.push(symbol)
  })
  let trends: TrendItem[] = []
  try {
    trends = await loadTrends(symbols, indicatorParams, nameBySymbol)
  } catch (error) {
    failures.push(failureOf('拉取指数日线', error))
  }

  return { estimate, holding, catalog, indexes, indicatorParams, trends, failures }
}

/** 判断一份数据里是否有可展示的内容 */
export function hasContent(payload: OverviewPayload): boolean {
  return (
    (payload.estimate.items?.length ?? 0) > 0 ||
    (payload.estimate.unavailableItems?.length ?? 0) > 0 ||
    payload.indexes.some((item) => item.change !== null)
  )
}

/** 整体冷暖结论，按指数涨跌的广度与幅度给一句话 */
export function marketMoodOf(indexes: IndexPulse[]): string {
  const values = indexes
    .map((item) => item.change)
    .filter((value): value is number => value !== null)
  if (values.length === 0) {
    return '指数不可用'
  }
  const up = values.filter((value) => value > 0).length
  const down = values.filter((value) => value < 0).length
  const average = values.reduce((sum, value) => sum + value, 0) / values.length
  if (up === values.length) {
    return `普涨 ${average.toFixed(2)}%`
  }
  if (down === values.length) {
    return `普跌 ${average.toFixed(2)}%`
  }
  if (Math.abs(average) < 0.2) {
    return `横盘 ${average.toFixed(2)}%`
  }
  return `${up > down ? '偏强' : '偏弱'} ${average.toFixed(2)}%`
}
