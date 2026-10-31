/**
 * 复盘数据仓库：取当日逐只对照、写本地缓存、断网回放，并支持手动触发晚间校准
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  applyGapReasons,
  hasContent,
  loadReview,
  loadTrend,
  netWorthLikelyPublished,
  runCalibrationFor,
  today,
} from '@/services/review'
import type { LoadFailure, ReviewPayload, ReviewRow } from '@/services/review'
import {
  CACHE_KEYS,
  formatStamp,
  offline,
  readSnapshot,
  watchOnline,
  writeSnapshot,
} from '@/services/offlineCache'
import type { Snapshot } from '@/services/offlineCache'

/** 一份空数据，字段与真实数据同构 */
function emptyPayload(tradeDate: string): ReviewPayload {
  return { tradeDate, rows: [], suspendHints: [], failures: [] }
}

export const useReviewStore = defineStore('review', () => {
  /** 当前复盘日期 */
  const tradeDate = ref(today())

  /** 页面数据，成功一次就留住，失败时不清空 */
  const payload = ref<ReviewPayload>(emptyPayload(tradeDate.value))

  /** 首次加载中 */
  const loading = ref(true)

  /** 刷新或校准中 */
  const busy = ref(false)

  /** 当前展示的数据是哪一刻取回的 */
  const shownAt = ref<string | null>(null)

  /** 当前展示的数据是否已过期 */
  const stale = ref(false)

  /** 本次失败的环节 */
  const failures = ref<LoadFailure[]>([])

  /** 是否断网 */
  const isOffline = computed(() => offline.value)

  /** 是否在显示旧数据 */
  const showingCache = computed(() => stale.value)

  /** 数据截止时间 */
  const dataAsOf = computed(() => formatStamp(shownAt.value))

  /** 逐只对照 */
  const rows = computed(() => payload.value.rows)

  /** 已校准出数的行 */
  const doneRows = computed(() => rows.value.filter((row) => !row.pending))

  /** 待校准的行 */
  const pendingRows = computed(() => rows.value.filter((row) => row.pending))

  /** 超阈值的行 */
  const overRows = computed(() => rows.value.filter((row) => row.overThreshold))

  /** 混合型行，按方向与区间判定 */
  const mixedRows = computed(() => doneRows.value.filter((row) => row.category === 'MIXED'))

  /** 点估计行 */
  const pointRows = computed(() => doneRows.value.filter((row) => row.category !== 'MIXED'))

  /** 点估计平均绝对误差 */
  const meanAbsError = computed(() => {
    const values = pointRows.value
      .map((row) => row.error)
      .filter((value): value is number => value !== null && value !== undefined)
    if (!values.length) {
      return null
    }
    return values.reduce((sum, value) => sum + Math.abs(value), 0) / values.length
  })

  /** 混合型方向命中率 */
  const directionAccuracy = computed(() => {
    const values = mixedRows.value
      .map((row) => row.directionHit)
      .filter((value): value is boolean => value !== null && value !== undefined)
    if (!values.length) {
      return null
    }
    return (values.filter(Boolean).length / values.length) * 100
  })

  /** 混合型区间覆盖率 */
  const intervalCoverage = computed(() => {
    const values = mixedRows.value
      .map((row) => row.inInterval)
      .filter((value): value is boolean => value !== null && value !== undefined)
    if (!values.length) {
      return null
    }
    return (values.filter(Boolean).length / values.length) * 100
  })

  /** 建议停用预估的基金 */
  const suspendHints = computed(() => payload.value.suspendHints)

  /** 最近一次校准时间，全部待校准时为空 */
  const calibratedAt = computed(() => formatStamp(payload.value.calibratedAt))

  /** 单只基金近期误差序列，键为品种主键 */
  const trendCache = ref<Record<string, API.EstimateErrorVO[]>>({})

  /** 最近一次校准返回的缺口原因，键为品种主键；缺口只在运行结果里，不落库 */
  const gapReasons = ref<Record<string, string>>({})

  /** 本会话已自动补过校准的交易日，同一天最多补一次，避免反复打上游 */
  const autoFilledDates = ref<Set<string>>(new Set())

  /** 待校准行的原因，取最近一次校准的缺口说明，没有则用通用文案 */
  function reasonOf(varietyKey: string): string {
    return (
      gapReasons.value[varietyKey] ??
      '该品种当日无误差记录：可能净值未公布、预估留存缺失或当时未给出可对照输出'
    )
  }

  /** 落缓存，按交易日分区，换天不会串数据 */
  function persist(next: ReviewPayload, fetchedAt: string): void {
    const snapshot: Snapshot<ReviewPayload> = {
      data: next,
      fetchedAt,
      dataDate: next.tradeDate,
    }
    writeSnapshot(CACHE_KEYS.review, snapshot)
  }

  /** 从本地缓存回放，只认同一个交易日的 */
  function replayFromCache(): boolean {
    const snapshot = readSnapshot<ReviewPayload>(CACHE_KEYS.review)
    // 缓存是上个交易日的就不回放，否则会把昨天的误差当今天的展示
    if (!snapshot || snapshot.data.tradeDate !== tradeDate.value) {
      return false
    }
    payload.value = { ...emptyPayload(tradeDate.value), ...snapshot.data }
    shownAt.value = snapshot.fetchedAt
    stale.value = true
    return true
  }

  /**
   * 拉取当日复盘数据
   *
   * @param force 是否强制刷新
   */
  async function load(force = false): Promise<void> {
    if (busy.value && !force) {
      return
    }
    busy.value = true
    try {
      const next = await loadReview(tradeDate.value)
      failures.value = next.failures
      if (hasContent(next)) {
        const fetchedAt = new Date().toISOString()
        payload.value = next
        shownAt.value = fetchedAt
        stale.value = false
        persist(next, fetchedAt)
      } else if (hasContent(payload.value)) {
        // 本次一条都取不到：保留上次结果，只标为过期
        stale.value = true
      } else if (!replayFromCache()) {
        payload.value = next
        shownAt.value = new Date().toISOString()
        stale.value = false
      }
    } catch (error) {
      failures.value = [
        {
          stage: '拉取复盘数据',
          message: error instanceof Error ? error.message : '未知错误',
          happenedAt: new Date().toISOString(),
        },
      ]
      stale.value = true
      if (!hasContent(payload.value)) {
        replayFromCache()
      }
    } finally {
      loading.value = false
      busy.value = false
    }
    // 净值已经公布但误差还没出的，补跑一次校准再回读，这就是「已公布却不刷新」的修复点
    if (hasContent(payload.value) && shouldAutoFill()) {
      await autoFillPending()
    }
  }

  /**
   * 是否满足自动补校准条件：净值大概率已公布、本会话没补过这一天、且有待校准行
   */
  function shouldAutoFill(): boolean {
    if (autoFilledDates.value.has(tradeDate.value)) {
      return false
    }
    if (!netWorthLikelyPublished(tradeDate.value)) {
      return false
    }
    return rows.value.some((row) => row.pending)
  }

  /**
   * 只对待校准的基金补跑一次校准，跑完把缺口原因补回行上
   */
  async function autoFillPending(): Promise<void> {
    autoFilledDates.value.add(tradeDate.value)
    const keys = rows.value.filter((row) => row.pending).map((row) => row.varietyKey)
    try {
      const result = await runCalibrationFor(tradeDate.value, keys)
      const next: Record<string, string> = {}
      ;(result.gaps ?? []).forEach((gap) => {
        if (gap.varietyKey && gap.reason) {
          next[gap.varietyKey] = gap.reason
        }
      })
      gapReasons.value = { ...gapReasons.value, ...next }
      busy.value = true
      try {
        const refreshed = await loadReview(tradeDate.value)
        const fetchedAt = new Date().toISOString()
        payload.value = refreshed
        shownAt.value = fetchedAt
        stale.value = false
        persist(refreshed, fetchedAt)
        // 校准结果里的缺口原因只在本次运行里有，补到待校准行上再渲染
        payload.value = {
          ...payload.value,
          notice: result.notice,
          rows: applyGapReasons(payload.value.rows, result.gaps ?? []),
        }
      } finally {
        busy.value = false
      }
    } catch {
      // 补校准失败不影响页面既有结果，行上的通用文案仍可读
    }
  }

  /**
   * 手动跑一次晚间校准，跑完立刻重取对照
   *
   * @returns 本次校准的缺口与说明
   */
  async function calibrate(): Promise<API.CalibrationResultVO> {
    busy.value = true
    try {
      const result = await runCalibrationFor(tradeDate.value)
      // 缺口原因只在本次运行结果里，补到待校准行上再渲染
      const next: Record<string, string> = {}
      ;(result.gaps ?? []).forEach((gap) => {
        if (gap.varietyKey && gap.reason) {
          next[gap.varietyKey] = gap.reason
        }
      })
      gapReasons.value = next
      // load 会整体替换 payload，notice 必须在它之后再补；手动跑过的当天不再自动补
      autoFilledDates.value.add(tradeDate.value)
      await load(true)
      payload.value = {
        ...payload.value,
        notice: result.notice,
        rows: applyGapReasons(payload.value.rows, result.gaps ?? []),
      }
      return result
    } finally {
      busy.value = false
    }
  }

  /**
   * 取某只基金近期的误差序列，已取过的直接复用
   *
   * @param varietyKey 品种业务主键
   */
  async function trendOf(varietyKey: string): Promise<API.EstimateErrorVO[]> {
    const cached = trendCache.value[varietyKey]
    if (cached) {
      return cached
    }
    const trend = await loadTrend(varietyKey)
    const series = trend.series ?? []
    trendCache.value = { ...trendCache.value, [varietyKey]: series }
    return series
  }

  /** 切到另一个交易日 */
  function setTradeDate(next: string): void {
    if (next === tradeDate.value) {
      return
    }
    tradeDate.value = next
    payload.value = emptyPayload(next)
    shownAt.value = null
    stale.value = false
    failures.value = []
    gapReasons.value = {}
  }

  /** 首次进页面：同交易日的缓存先渲染，再后台补取 */
  async function bootstrap(): Promise<void> {
    if (!hasContent(payload.value)) {
      replayFromCache()
    }
    await load()
  }

  /** 监听上下线，恢复后自动补取 */
  function trackConnectivity(): () => void {
    return watchOnline((online) => {
      if (online) {
        void load(true)
      }
    })
  }

  return {
    tradeDate,
    payload,
    loading,
    busy,
    shownAt,
    stale,
    failures,
    isOffline,
    showingCache,
    dataAsOf,
    rows,
    doneRows,
    pendingRows,
    overRows,
    mixedRows,
    pointRows,
    meanAbsError,
    directionAccuracy,
    intervalCoverage,
    suspendHints,
    calibratedAt,
    gapReasons,
    reasonOf,
    load,
    calibrate,
    trendOf,
    setTradeDate,
    bootstrap,
    trackConnectivity,
  }
})

/** 对外暴露行类型，页面直接用 */
export type { ReviewRow }
