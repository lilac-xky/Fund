<template>
  <div class="min-h-screen bg-[var(--canvas)]">
    <AppTopBar active="indicator">
      <template #status>
        {{ loading ? '正在取数' : `${bars.length} 根日线` }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimable-count="indicator?.complete ? 1 : 0"
      :total-count="1"
      :market-mood="paramText"
      :next-note="barDate ? `数据截止 ${barDate}` : ''"
    />

    <div class="pt-[calc(var(--topbar)+var(--pulse))] max-[860px]:pt-[var(--topbar)]">
      <div class="mx-auto max-w-[1180px] px-5 pb-16">
        <div class="app-head mb-4">
          <div class="app-crumb">
            行情 <span class="app-crumb__sl">/</span>
            <b class="app-crumb__b">技术指标</b>
          </div>
          <h1 class="app-h1">指标图表</h1>
          <p class="app-lede">
            K 线叠加 MACD、RSI 与 KDJ，参数与参数设置页的 INDICATOR 分组一致。
            图表库随构建产物本地打包，断网仍可打开。
          </p>
        </div>

        <AppGroup
          title="图表"
          code="CHART"
          :meta="symbol || '尚未选择标的'"
          :meta-ok="Boolean(symbol)"
        >
          <template #icon>
            <span v-html="CHART_ICON" />
          </template>

          <div class="mb-3 flex flex-wrap items-center gap-2">
            <label class="text-[12px] text-[var(--t3)]">标的</label>
            <select v-model="varietyKey" class="app-select" @change="loadByVariety">
              <option value="">— 选择持仓 —</option>
              <option v-for="row in watchRows" :key="row.varietyKey" :value="row.varietyKey">
                {{ row.varietyName }}（{{ row.varietyCode }}）{{ row.onExchange ? '' : ' · 场外' }}
              </option>
            </select>
            <span class="app-basis">{{ basisLabel }}</span>
            <span v-if="errorText" class="app-alert-line">{{ errorText }}</span>
          </div>

          <div v-if="!symbol" class="app-listempty">
            先选一个持仓标的；没有持仓时请到「持仓」页录入。
          </div>
          <template v-else>
            <div v-if="loading" class="app-loading">正在取数…</div>
            <div v-else-if="!bars.length" class="app-listempty">{{ emptyHint }}</div>
            <div v-show="bars.length > 0" class="app-chgbar">
              <span
                >最新 <b>{{ fmt(lastClose) }}</b></span
              >
              <span :class="toneClass(lastChange)">{{ signedPercent(lastChange) }}</span>
              <span>
                区间
                <span :class="toneClass(rangeChange)">{{ signedPercent(rangeChange) }}</span>
              </span>
              <span class="app-chgbar__note">
                第二栏为逐日涨跌幅（{{ basis === 'NET_WORTH' ? '净值日变动' : '按收盘价' }}）， RSI
                栏 30/70 为超卖超买参考线
              </span>
            </div>
            <!-- 容器必须常驻 DOM：render() 在 loading 翻 false 之前执行，放进条件链会在那一刻拿不到节点 -->
            <div v-show="bars.length > 0" ref="chartHost" class="app-chart" />
          </template>

          <p v-if="indicator" class="app-disclaimer">
            接口计算：RSI {{ fmt(indicator.rsi) }} · K {{ fmt(indicator.k) }} · D
            {{ fmt(indicator.d) }} · DIF {{ fmt(indicator.dif) }} · DEA {{ fmt(indicator.dea) }}。
            <template v-if="basis === 'NET_WORTH'">
              场外基金按单位净值计算，没有日线最高最低价，故 KDJ 无法计算；主图是净值折线而非 K
              线，副图 RSI 与 MACD 有效。
            </template>
            <template v-else-if="indicator.adjusted === false"
              >日线为不复权口径（备用源），与行情软件可能有差异。</template
            >
            <template v-else>日线为前复权口径。</template>
          </p>
        </AppGroup>

        <p class="app-disclaimer">
          图表由本页按接口返回的序列现算副图，主图指标数值与后端计算同口径（EMA 首值作种子、KDJ 以
          50 起递推）。 两处小差异属正常：图表按全部返回序列连续计算，后端按参数所需最少根数计算。
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 指标图表页：K 线叠加 MACD、RSI、KDJ，ECharts 按需引入并本地打包
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, CandlestickChart, LineChart } from 'echarts/charts'
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

import AppGroup from '@/ui/AppGroup.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import { history as quoteHistory } from '@/api/marketQuoteController'
import { one as indicatorOne } from '@/api/indicatorController'
import { snapshot } from '@/api/fundSnapshotController'
import { overview as holdingOverview } from '@/api/holdingController'
import { list as paramList } from '@/api/strategyParamController'
import { DEFAULT_INDICATOR_PARAMS } from '@/services/indicators'
import { buildOption, paramSummary } from '@/services/indicatorChart'
import type { Bar, ChartParams } from '@/services/indicatorChart'

// 按需注册，避免把整个 ECharts 打进产物；全部来自本地依赖，无任何 CDN 请求
echarts.use([
  BarChart,
  CandlestickChart,
  LineChart,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer,
])

/** 分组图标，内联 SVG 以免引图标库 */
const CHART_ICON =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 20V4"/><path d="M4 20h16"/><path d="M8 9v6M8 12h0M12 6v9M16 10v7M20 8v5"/></svg>'

/** 日线根数，够算全部指标并留出观察窗口 */
const BAR_LIMIT = 250

/** 持仓行，供标的下拉 */
interface WatchRow {
  varietyKey: string
  varietyCode: string
  varietyName: string
  /** 是否场内可交易，场内走日线，场外走净值 */
  onExchange: boolean
}

const chartHost = ref<HTMLElement | null>(null)
const chart = shallowRef<echarts.ECharts | null>(null)
const loading = ref(false)
const errorText = ref('')
const watchRows = ref<WatchRow[]>([])
const varietyKey = ref('')
const bars = ref<Bar[]>([])
const symbol = ref('')
const barDate = ref('')
/** 当前输入口径，场内走日线，场外走净值 */
const basis = ref<'DAILY_BAR' | 'NET_WORTH'>('DAILY_BAR')
const indicator = ref<API.IndicatorVO | null>(null)
const params = ref<ChartParams>({
  ...DEFAULT_INDICATOR_PARAMS,
  kdjPeriod: 9,
  kdjKSmooth: 3,
  kdjDSmooth: 3,
})

/**
 * 参数摘要文字，顶栏与图内一致
 */
const paramText = computed(() => paramSummary(params.value))

/** 最新收盘价或净值 */
const lastClose = computed(() => {
  const closes = bars.value.map((bar) => bar.close ?? 0).filter((value) => value > 0)
  return closes.length ? closes[closes.length - 1]! : null
})

/** 最新一天的涨跌幅，百分点 */
const lastChange = computed(() => {
  const closes = bars.value.map((bar) => bar.close ?? 0)
  if (closes.length < 2 || !closes[closes.length - 2]) {
    return null
  }
  return percentChange(closes[closes.length - 2]!, closes[closes.length - 1] ?? 0)
})

/** 最近 20 个交易日的区间涨跌幅，百分点 */
const rangeChange = computed(() => {
  const closes = bars.value.map((bar) => bar.close ?? 0).filter((value) => value > 0)
  if (closes.length < 2) {
    return null
  }
  const window = closes.slice(-21)
  return percentChange(window[0]!, window[window.length - 1] ?? 0)
})

/** 口径标签，让使用者一眼看出看的是日线还是净值 */
const basisLabel = computed(() => (basis.value === 'NET_WORTH' ? '净值口径' : '日线口径'))

/** 无数据时的提示，场外与场内原因不同 */
const emptyHint = computed(() =>
  basis.value === 'NET_WORTH'
    ? '该基金库内还没有净值序列。请先到「持仓」页打开一次持仓总览，或等晚间校准拉取净值后再看。'
    : '该标的没有可用日线，可能是代码不是场内标的，或行情源本次取数失败。',
)

/**
 * 拉参数设置页的 INDICATOR 分组，取不到时用内置默认值
 *
 * @returns 无
 */
async function loadParams(): Promise<void> {
  try {
    const result = await paramList({ paramGroup: 'INDICATOR' })
    const rows = result.data ?? []
    const value = (key: string): string | undefined =>
      rows.find((row) => row.paramKey === key)?.paramValue ?? undefined
    params.value = {
      rsiPeriod: Number(value('rsiPeriod') ?? DEFAULT_INDICATOR_PARAMS.rsiPeriod),
      kdjPeriod: Number(value('kdjPeriod') ?? 9),
      kdjKSmooth: Number(value('kdjKSmooth') ?? 3),
      kdjDSmooth: Number(value('kdjDSmooth') ?? 3),
      macdFast: Number(value('macdFast') ?? DEFAULT_INDICATOR_PARAMS.macdFast),
      macdSlow: Number(value('macdSlow') ?? DEFAULT_INDICATOR_PARAMS.macdSlow),
      macdSignal: Number(value('macdSignal') ?? DEFAULT_INDICATOR_PARAMS.macdSignal),
    }
  } catch {
    // 参数取不到不该让图表打不开，用默认值继续
    errorText.value = '指标参数读取失败，图表按默认参数绘制'
  }
}

/**
 * 拉持仓作为标的下拉，默认选第一个
 *
 * @returns 无
 */
async function loadHoldings(): Promise<void> {
  try {
    const result = await holdingOverview()
    const rows: API.HoldingVarietyVO[] = result.data?.items ?? []
    watchRows.value = rows
      .filter((row) => row.varietyKey && row.varietyCode)
      .map((row) => ({
        varietyKey: row.varietyKey ?? '',
        varietyCode: row.varietyCode ?? '',
        varietyName: row.varietyName ?? row.varietyKey ?? '',
        onExchange: row.varietyType === 'ETF',
      }))
    if (!varietyKey.value && watchRows.value.length > 0) {
      varietyKey.value = watchRows.value[0]!.varietyKey
      await loadByVariety()
    }
  } catch {
    errorText.value = '持仓读取失败，请确认已登录且已录入持仓'
  }
}

/**
 * 按当前品种取日线，再取后端指标并重绘
 *
 * @returns 无
 */
async function loadByVariety(): Promise<void> {
  const row = watchRows.value.find((item) => item.varietyKey === varietyKey.value)
  if (!row) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    // 场外基金没有日线行情，改用历史净值当收盘价，否则永远是 0 根
    if (row.onExchange) {
      await loadByDailyBar(row)
    } else {
      await loadByNetWorth(row)
    }
    await loadIndicator(row.varietyKey)
    await nextTick()
    render()
  } catch {
    errorText.value = '取数失败，图表无法绘制'
    bars.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 场内 ETF 走日线行情，含开高低收量，三个指标都能算
 *
 * @param row 标的
 * @returns 无
 */
async function loadByDailyBar(row: WatchRow): Promise<void> {
  const historyResult = await quoteHistory({ codes: [row.varietyCode], limit: BAR_LIMIT })
  const history = (historyResult.data ?? [])[0]
  symbol.value = history?.symbol ?? row.varietyCode
  barDate.value = history?.latestDate ?? ''
  basis.value = 'DAILY_BAR'
  bars.value = (history?.bars ?? []).map((bar) => ({
    date: bar.date,
    close: Number(bar.close ?? 0),
    open: bar.open === null || bar.open === undefined ? undefined : Number(bar.open),
    high: bar.high === null || bar.high === undefined ? undefined : Number(bar.high),
    low: bar.low === null || bar.low === undefined ? undefined : Number(bar.low),
    volume: bar.volume === null || bar.volume === undefined ? undefined : Number(bar.volume),
  }))
}

/**
 * 场外基金走历史净值，没有最高最低价故 KDJ 不可算
 *
 * @param row 标的
 * @returns 无
 */
async function loadByNetWorth(row: WatchRow): Promise<void> {
  const result = await snapshot({ code: row.varietyCode })
  const points = result.data?.netWorthPoints ?? []
  symbol.value = row.varietyCode
  barDate.value = result.data?.netWorthDate ?? ''
  basis.value = 'NET_WORTH'
  // 净值序列没有开高低与成交量，只把净值当收盘价，图上按折线而非 K 线呈现
  bars.value = points
    .filter((point) => point.unitNetWorth !== null && point.unitNetWorth !== undefined)
    .map((point) => ({ date: point.netWorthDate, close: Number(point.unitNetWorth ?? 0) }))
}

/**
 * 取后端算好的指标数值，用于与图表对照
 *
 * @param key 品种业务主键
 * @returns 无
 */
async function loadIndicator(key: string): Promise<void> {
  try {
    const result = await indicatorOne({ varietyKey: key })
    indicator.value = result.data ?? null
  } catch {
    // 指标接口失败只影响对照文字，图表仍可用
    indicator.value = null
  }
}

/**
 * 用当前日线与参数重绘图表
 *
 * @returns 无
 */
function render(): void {
  if (!chartHost.value || bars.value.length === 0) {
    return
  }
  // 实例与容器必须是同一个节点：容器被条件渲染销毁重建后旧实例仍在，setOption 会画进游离节点
  if (chart.value && chart.value.getDom() !== chartHost.value) {
    chart.value.dispose()
    chart.value = null
  }
  if (!chart.value) {
    chart.value = echarts.init(chartHost.value)
  }
  // 容器刚挂载时高度可能是 0，画了也看不见；推迟到布局完成后再画一次
  if (chartHost.value.clientHeight === 0) {
    void nextTick(() => render())
    return
  }
  chart.value.setOption(buildOption(bars.value, params.value, basis.value), true)
}

/**
 * 窗口尺寸变化时重算图表大小
 *
 * @returns 无
 */
function onResize(): void {
  chart.value?.resize()
}

onMounted(async () => {
  await loadParams()
  await loadHoldings()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart.value?.dispose()
  chart.value = null
})

// 参数改动后按新参数重算副图，与计算层保持一致
watch(params, () => render(), { deep: true })

/**
 * 数值展示，缺值给破折号
 *
 * @param value 数值
 * @returns 文本
 */
function fmt(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return Number(value).toFixed(2)
}

/** 带正负号的百分比 */
function signedPercent(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return `${value > 0 ? '+' : ''}${Number(value).toFixed(2)}%`
}

/** 涨跌配色，涨暖跌冷，零与缺值中性 */
function toneClass(value?: number | null): string {
  if (value === undefined || value === null || value === 0) {
    return ''
  }
  return value > 0 ? 'app-chgbar__up' : 'app-chgbar__down'
}

/** 两点之间的涨跌幅，百分点，基准为 0 时返回 null */
function percentChange(previous: number, current: number): number | null {
  if (!previous) {
    return null
  }
  return Number((((current - previous) / previous) * 100).toFixed(3))
}
</script>

<style scoped>
.app-chart {
  width: 100%;
  height: 700px;
}

.app-chgbar {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 12.5px;
  color: var(--t2);
  font-variant-numeric: tabular-nums;
}

.app-chgbar b {
  font-size: 14px;
  color: var(--t1);
}

.app-chgbar__note {
  font-size: 11px;
  color: var(--t4);
}

.app-chgbar__up {
  color: var(--up);
}

.app-chgbar__down {
  color: var(--dn);
}

.app-select {
  height: 30px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--t1);
  font-size: 12.5px;
}

.app-alert-line {
  font-size: 12px;
  color: var(--warn);
}

.app-basis {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--acc-soft);
  color: var(--acc);
  font-size: 11.5px;
  box-shadow: inset 0 0 0 1px var(--acc-line);
}
</style>
