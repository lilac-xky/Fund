/**
 * 总览数据仓库：统一发起取数、写本地缓存、断网回放与恢复补取
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  hasContent,
  loadOverview,
  marketMoodOf,
  type LoadFailure,
  type OverviewPayload,
} from '@/services/overview'
import {
  formatStamp,
  offline,
  readSnapshot,
  watchOnline,
  writeSnapshot,
  type Snapshot,
} from '@/services/offlineCache'

/** 一份空数据，首次渲染与无缓存时使用，字段与真实数据同构 */
function emptyPayload(): OverviewPayload {
  return {
    estimate: {},
    holding: {},
    catalog: {},
    indexes: [],
    indicatorParams: { rsiPeriod: 14, macdFast: 12, macdSlow: 26, macdSignal: 9 },
    trends: [],
    failures: [],
  }
}

export const useOverviewStore = defineStore('overview', () => {
  /** 页面数据，成功一次就留住，失败时不清空 */
  const payload = ref<OverviewPayload>(emptyPayload())

  /** 首次加载中 */
  const loading = ref(true)

  /** 刷新中 */
  const refreshing = ref(false)

  /** 当前展示的数据是哪一刻取回的，ISO 字符串 */
  const shownAt = ref<string | null>(null)

  /** 当前展示的数据是否已过期，断网或本次取数失败时为 true */
  const stale = ref(false)

  /** 本次请求失败的环节，成功后清空 */
  const failures = ref<LoadFailure[]>([])

  /** 是否处于断网状态 */
  const isOffline = computed(() => offline.value)

  /** 是否只显示缓存：断网或本次请求失败 */
  const showingCache = computed(() => stale.value)

  /** 数据截止时间，可直接展示 */
  const dataAsOf = computed(() => formatStamp(shownAt.value))

  /** 后端给出的行情日期 */
  const quoteDate = computed(() => payload.value.estimate.dataDate ?? '—')

  /** 整体冷暖 */
  const marketMood = computed(() => marketMoodOf(payload.value.indexes))

  /** 可预估只数 */
  const estimableCount = computed(() => payload.value.estimate.estimableCount ?? 0)

  /** 持仓只数 */
  const totalCount = computed(
    () => payload.value.estimate.requested ?? payload.value.holding.varietyCount ?? 0,
  )

  /** 整体预估涨跌幅，只有 A 档参与 */
  const overallChange = computed(() => payload.value.estimate.overallChange ?? null)

  /** 可预估的基金 */
  const items = computed(() => payload.value.estimate.items ?? [])

  /** 不可预估的基金 */
  const unavailableItems = computed(() => payload.value.estimate.unavailableItems ?? [])

  /** 走势，按涨跌幅降序，取不到的排最后 */
  const trends = computed(() =>
    [...payload.value.trends].sort((left, right) => (right.change ?? -999) - (left.change ?? -999)),
  )

  /** 行情条要用的指数 */
  const indexes = computed(() =>
    payload.value.indexes
      .filter((item) => item.change !== null)
      .map((item) => ({ name: item.name, change: item.change ?? 0 })),
  )

  /** 日线数据日期，页面标注用 */
  const trendAsOf = computed(() => {
    const dates = payload.value.trends
      .map((item) => item.latestDate)
      .filter((value): value is string => Boolean(value))
    return dates.sort().reverse()[0] ?? ''
  })

  /** 落缓存，写的是本次成功的数据与补取时间 */
  function persist(next: OverviewPayload, fetchedAt: string): void {
    const snapshot: Snapshot<OverviewPayload> = {
      data: next,
      fetchedAt,
      dataDate: next.estimate.dataDate,
    }
    writeSnapshot(snapshot)
  }

  /** 从本地缓存回放，返回是否命中 */
  function replayFromCache(): boolean {
    const snapshot = readSnapshot<OverviewPayload>()
    if (!snapshot) {
      return false
    }
    payload.value = { ...emptyPayload(), ...snapshot.data }
    shownAt.value = snapshot.fetchedAt
    stale.value = true
    return true
  }

  /**
   * 拉取数据，失败时保留上次结果，无结果时回放缓存
   *
   * @param force 是否跳过后端缓存强制重算
   */
  async function load(force = false): Promise<void> {
    if (refreshing.value) {
      return
    }
    refreshing.value = true
    try {
      const next = await loadOverview(force)
      failures.value = next.failures
      if (hasContent(next)) {
        const fetchedAt = new Date().toISOString()
        payload.value = next
        shownAt.value = fetchedAt
        stale.value = false
        persist(next, fetchedAt)
      } else if (hasContent(payload.value)) {
        // 本次一条都取不到：保留上次成功结果，只标为过期
        stale.value = true
      } else if (!replayFromCache()) {
        payload.value = next
        shownAt.value = new Date().toISOString()
        stale.value = false
      }
    } catch (error) {
      // 断网与超时属整请求失败：保留上次结果，没有结果再回放缓存
      failures.value = [
        {
          stage: '拉取总览数据',
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
      refreshing.value = false
    }
  }

  /** 首次进页面：有缓存先渲染，再后台补取 */
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
        void load()
      }
    })
  }

  return {
    payload,
    loading,
    refreshing,
    shownAt,
    stale,
    failures,
    isOffline,
    showingCache,
    dataAsOf,
    quoteDate,
    marketMood,
    estimableCount,
    totalCount,
    overallChange,
    items,
    unavailableItems,
    trends,
    indexes,
    trendAsOf,
    load,
    bootstrap,
    trackConnectivity,
  }
})
