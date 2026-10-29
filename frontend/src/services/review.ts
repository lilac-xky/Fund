/**
 * 复盘页数据编排：把当日误差记录与持仓名单合成逐只对照，未出数的标为「待校准」
 */
import {
  errors as errorHistory,
  run as runCalibration,
  suspendHints as suspendHintsApi,
  trend as errorTrend,
} from '@/api/estimateCalibrationController'
import { overview as holdingOverview } from '@/api/holdingController'
import { RequestError } from '@/request'

/** 一只基金的当日对照结果 */
export interface ReviewRow {
  /** 品种业务主键 */
  varietyKey: string
  /** 6 位代码 */
  varietyCode?: string
  /** 标的名称 */
  varietyName?: string
  /** 基金类型 */
  category?: string
  /** 基金类型中文名 */
  categoryLabel?: string
  /** 当时给出的预估涨跌幅，百分点；B、C 档为 null */
  estimated?: number | null
  /** 当日实际涨跌幅，百分点 */
  actual?: number | null
  /** 误差，预估减实际，百分点 */
  error?: number | null
  /** 本次判定所用阈值，百分点；混合型为 null */
  threshold?: number | null
  /** 是否超阈值 */
  overThreshold: boolean
  /** 方向是否命中，混合型才有 */
  directionHit?: boolean | null
  /** 实际值是否落在区间内，混合型才有 */
  inInterval?: boolean | null
  /** 当时区间下限 */
  intervalLow?: number | null
  /** 当时区间上限 */
  intervalHigh?: number | null
  /** 所给区间宽度，百分点；误差表只存宽度不存上下界 */
  intervalWidth?: number | null
  /** 当时输出档位 */
  outputTier?: string
  /** 预估来源 */
  estimateSource?: string
  /** 该条误差的写入时间，即校准时间 */
  calibratedAt?: string
  /** 是否尚未校准出数 */
  pending: boolean
  /** 待校准原因，仅 pending 时有值 */
  pendingReason?: string
}

/** 一路数据的失败记录 */
export interface LoadFailure {
  /** 出错环节 */
  stage: string
  /** 原因 */
  message: string
  /** 发生时间，ISO 字符串 */
  happenedAt: string
}

/** 整页数据 */
export interface ReviewPayload {
  /** 复盘交易日 */
  tradeDate: string
  /** 逐只对照 */
  rows: ReviewRow[]
  /** 建议停用预估的基金 */
  suspendHints: API.EstimateErrorTrendVO[]
  /** 本次校准说明 */
  notice?: string
  /** 最近一次校准写入时间，全部待校准时为空 */
  calibratedAt?: string
  /** 本次失败的环节 */
  failures: LoadFailure[]
}

/** 从错误对象里取出可读文案 */
function messageOf(error: unknown): string {
  if (error instanceof RequestError) {
    return `${error.message}（${error.happenedAt}）`
  }
  return error instanceof Error ? error.message : '未知错误'
}

/** 记录一次失败 */
function failureOf(stage: string, error: unknown): LoadFailure {
  return { stage, message: messageOf(error), happenedAt: new Date().toISOString() }
}

/** 今天的日期，按本地时区取 yyyy-MM-dd */
export function today(): string {
  const now = new Date()
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/**
 * 把持仓名单与当日误差记录合成逐只对照，缺记录的一律标为待校准
 *
 * @param roster  持仓品种
 * @param records 当日误差记录
 * @returns 逐只对照
 */
function merge(roster: API.HoldingVarietyVO[], records: API.EstimateErrorVO[]): ReviewRow[] {
  const byKey = new Map<string, API.EstimateErrorVO>()
  records.forEach((record) => {
    if (record.varietyKey) {
      byKey.set(record.varietyKey, record)
    }
  })
  const rows: ReviewRow[] = roster.map((item) => {
    const key = item.varietyKey ?? ''
    const record = byKey.get(key)
    byKey.delete(key)
    if (!record) {
      const pendingRow: ReviewRow = {
        varietyKey: key,
        varietyCode: item.varietyCode,
        varietyName: item.varietyName,
        category: item.category,
        categoryLabel: item.categoryLabel,
        overThreshold: false,
        pending: true,
        pendingReason: '当日误差记录尚未生成，待晚间净值公布后校准',
      }
      return pendingRow
    }
    return fromRecord(record, item)
  })
  // 误差表里有、持仓里已没有的品种也要列出来，历史误差不会因为清仓而失效
  byKey.forEach((record) => rows.push(fromRecord(record, undefined)))
  return rows
}

/** 一条误差记录转对照行 */
function fromRecord(
  record: API.EstimateErrorVO,
  fallback: API.HoldingVarietyVO | undefined,
): ReviewRow {
  return {
    varietyKey: record.varietyKey ?? fallback?.varietyKey ?? '',
    varietyCode: fallback?.varietyCode,
    varietyName: fallback?.varietyName,
    category: fallback?.category,
    categoryLabel: fallback?.categoryLabel,
    estimated: record.estimatedChange ?? null,
    actual: record.actualChange ?? null,
    error: record.errorValue ?? null,
    threshold: record.thresholdValue ?? null,
    overThreshold: Boolean(record.overThreshold),
    directionHit: record.directionHit ?? null,
    inInterval: record.inInterval ?? null,
    intervalLow: null,
    intervalHigh: null,
    intervalWidth: record.intervalWidth ?? null,
    outputTier: record.outputTier ?? undefined,
    estimateSource: record.estimateSource ?? undefined,
    calibratedAt: record.updateTime ?? record.createTime ?? undefined,
    pending: false,
  }
}

/**
 * 拉取某一交易日的复盘数据，先读误差历史，读不到再报缺口
 *
 * @param tradeDate 复盘交易日，yyyy-MM-dd
 */
export async function loadReview(tradeDate: string): Promise<ReviewPayload> {
  const failures: LoadFailure[] = []
  const [holdingResult, errorResult, hintResult] = await Promise.allSettled([
    holdingOverview(),
    errorHistory({ startDate: tradeDate, endDate: tradeDate }),
    suspendHintsApi({ tradeDate }),
  ])

  const roster = holdingResult.status === 'fulfilled' ? (holdingResult.value.data?.items ?? []) : []
  if (holdingResult.status === 'rejected') {
    failures.push(failureOf('拉取持仓名单', holdingResult.reason))
  }
  const records = errorResult.status === 'fulfilled' ? (errorResult.value.data ?? []) : []
  if (errorResult.status === 'rejected') {
    failures.push(failureOf('拉取当日误差记录', errorResult.reason))
  }
  const suspendHints = hintResult.status === 'fulfilled' ? (hintResult.value.data ?? []) : []
  if (hintResult.status === 'rejected') {
    failures.push(failureOf('拉取停用建议', hintResult.reason))
  }

  const rows = merge(roster, records)
  const calibratedAt = rows
    .map((row) => row.calibratedAt)
    .filter((value): value is string => Boolean(value))
    .sort()
    .reverse()[0]

  return { tradeDate, rows, suspendHints, calibratedAt, failures }
}

/**
 * 主动运行某一交易日的晚间校准，返回结果里带出逐只缺口原因
 *
 * @param tradeDate 校准交易日
 */
export async function runCalibrationFor(tradeDate: string): Promise<API.CalibrationResultVO> {
  const result = await runCalibration({ tradeDate })
  return result.data ?? {}
}

/**
 * 取单只基金近期的误差序列
 *
 * @param varietyKey 品种业务主键
 * @param days       回溯天数
 */
export async function loadTrend(varietyKey: string, days = 30): Promise<API.EstimateErrorTrendVO> {
  const result = await errorTrend({ varietyKey, days })
  return result.data ?? {}
}

/**
 * 用最近一次校准返回的缺口说明补充待校准行的原因
 *
 * @param rows   逐只对照
 * @param gaps   校准缺口
 * @returns 原因已补充的对照行
 */
export function applyGapReasons(rows: ReviewRow[], gaps: API.CalibrationGapVO[]): ReviewRow[] {
  if (!gaps.length) {
    return rows
  }
  const reasons = new Map<string, string>()
  gaps.forEach((gap) => {
    if (gap.varietyKey && gap.reason) {
      reasons.set(gap.varietyKey, gap.reason)
    }
  })
  return rows.map((row) => {
    const reason = row.pending ? reasons.get(row.varietyKey) : undefined
    return reason ? { ...row, pendingReason: reason } : row
  })
}

/** 是否有可展示的内容 */
export function hasContent(payload: ReviewPayload): boolean {
  return payload.rows.length > 0
}
