<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <AppTopBar active="overview">
      <template #status>
        {{ store.isOffline ? '离线，显示缓存' : store.refreshing ? '刷新中…' : '在线' }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimated-change="store.overallChange"
      :estimable-count="store.estimableCount"
      :total-count="store.totalCount"
      :market-mood="store.marketMood"
      :indexes="store.indexes"
      :market-value="store.payload.holding.totalMarketValue"
      :data-as-of="dataAsOfLabel"
      :next-note="pulseNote"
    />

    <div class="app-body">
      <AppSideRail
        section-label="盘中视角"
        :items="railItems"
        :active="activePane"
        footer-label="强制重算预估"
        :footer-icon="ICONS.refresh"
        @select="activePane = $event"
        @footer="onRefresh"
      />

      <div class="app-stage">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                总览 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">{{ activePane === 'morning' ? '早盘' : '午盘' }}</b>
              </div>
              <div class="app-eyebrow">Overview</div>
              <h1 class="app-h1">{{ activePane === 'morning' ? '早盘看大局' : '午盘看延续' }}</h1>
              <p class="app-lede">
                {{
                  activePane === 'morning'
                    ? '全部持仓的当日方向与整体冷暖，用来确认今天该偏进攻还是偏防守；此时不做买卖决策。'
                    : '上午行情是否延续早盘方向，看持仓相关指数的盘中走势与技术指标状态。'
                }}
              </p>
            </div>

            <!-- 断网或降级时的数据截止说明，必须与数据同屏，不能只给数据不说何时截止 -->
            <AppAlert v-if="store.isOffline" kind="warn" title="当前网络不可用">
              正在显示 {{ store.dataAsOf }} 取回的数据，恢复联网后会自动补取最新结果。
            </AppAlert>
            <AppAlert v-else-if="store.showingCache" kind="warn" title="本次取数未成功">
              接口未返回有效数据，当前显示的是 {{ store.dataAsOf }} 的结果，恢复后会自动补取。
            </AppAlert>
            <AppAlert v-if="failures.length" kind="error" title="部分环节出错">
              <span
                v-for="item in failures"
                :key="`${item.stage}-${item.happenedAt}`"
                class="app-fail"
              >
                {{ item.stage }}：{{ item.message }}（{{ fullTime(item.happenedAt) }}）
              </span>
              页面保留上一次成功的结果，未显示空白。
            </AppAlert>

            <div v-if="store.loading" class="app-loading">正在读取持仓与预估…</div>

            <!-- 空状态：尚未录入持仓 -->
            <div v-else-if="emptyState" class="app-empty">
              <div class="app-empty__ico" v-html="ICONS.compass" />
              <b>尚未录入持仓</b>
              <p>总览页的数据来自持仓。先录入基金或 ETF 代码，预估与冷暖摘要才会开始工作。</p>
              <RouterLink to="/holding" class="app-empty__btn">前往录入持仓</RouterLink>
            </div>

            <template v-else>
              <!-- 早盘：持仓预估涨跌表 -->
              <AppGroup
                v-show="activePane === 'morning'"
                title="持仓当日预估"
                code="ESTIMATE"
                :meta="estimateMeta"
                :meta-ok="!store.payload.estimate.degraded"
              >
                <template #icon>
                  <span v-html="ICONS.compass" />
                </template>

                <div v-if="!items.length" class="app-listempty">
                  当前没有可给出预估的基金，原因见下方「不可预估」区块。
                </div>

                <div v-else class="app-table">
                  <div class="app-table__head">
                    <span class="col-name">基金</span>
                    <span class="col-type">类型</span>
                    <span class="col-change">当日预估</span>
                    <span class="col-confidence">可信度</span>
                    <span class="col-note">误差说明</span>
                  </div>
                  <div v-for="item in items" :key="item.varietyKey" class="app-table__row">
                    <div class="col-name">
                      <b class="app-name">{{ item.varietyName ?? item.varietyKey }}</b>
                      <span class="app-code">{{ item.varietyCode }} · {{ item.branchLabel }}</span>
                    </div>
                    <div class="col-type">
                      <span
                        class="app-tag"
                        :style="{
                          color: categoryColor(item.category),
                          boxShadow: `inset 0 0 0 1px ${categoryColor(item.category)}`,
                        }"
                      >
                        {{ item.categoryLabel }}
                      </span>
                    </div>
                    <div class="col-change">
                      <span
                        v-if="item.estimatedChange !== null && item.estimatedChange !== undefined"
                        class="app-change"
                        :class="toneClass(item.estimatedChange)"
                      >
                        {{ signedPercent(item.estimatedChange) }}
                      </span>
                      <span v-else class="app-direction" :class="toneClass(directionTone(item))">
                        {{ item.directionLabel ?? '—' }}
                        <em v-if="item.intervalLow !== null && item.intervalHigh !== null">
                          {{ signedPercent(item.intervalLow) }}~{{
                            signedPercent(item.intervalHigh)
                          }}
                        </em>
                      </span>
                    </div>
                    <div class="col-confidence">
                      <span class="app-tier" :style="{ color: tierColor(item.tier) }">
                        {{ item.tier }} 档 · {{ item.confidenceLabel }}
                      </span>
                    </div>
                    <div class="col-note">
                      <span
                        v-for="(remark, index) in item.remarks ?? []"
                        :key="index"
                        class="app-remark"
                      >
                        {{ remark }}
                      </span>
                    </div>
                  </div>
                </div>

                <div class="app-foot">
                  <span
                    >整体预估 {{ signedPercent(store.overallChange) }}，只统计 A 档
                    {{ overallCount }} 只</span
                  >
                  <span v-if="store.payload.estimate.overallNote" class="app-foot__note">
                    {{ store.payload.estimate.overallNote }}
                  </span>
                  <span v-if="mixedCount" class="app-foot__note">
                    其中混合型 {{ mixedCount }} 只只给方向与区间，不给单一数字
                  </span>
                </div>
              </AppGroup>

              <!-- 不可预估：单独区块，逐只说明原因 -->
              <AppGroup
                v-show="activePane === 'morning'"
                title="不可预估"
                code="UNAVAILABLE"
                :meta="unavailableItems.length ? `${unavailableItems.length} 只` : '全部可预估'"
                :meta-ok="!unavailableItems.length"
              >
                <template #icon>
                  <span v-html="ICONS.unknown" />
                </template>

                <div v-if="!unavailableItems.length" class="app-listempty">
                  当前没有不可预估的基金。行情取不到或重仓股停牌时会出现在这里，并说明具体原因。
                </div>

                <div v-else class="app-unknown">
                  <div
                    v-for="item in unavailableItems"
                    :key="item.varietyKey"
                    class="app-unknown__row"
                  >
                    <div class="app-unknown__id">
                      <b>{{ item.varietyName ?? item.varietyKey }}</b>
                      <span class="app-code">
                        {{ item.varietyCode }} · {{ item.categoryLabel }}
                      </span>
                    </div>
                    <div class="app-unknown__why">
                      <span class="app-unknown__reason">{{
                        item.unavailableReason ?? '暂无法预估'
                      }}</span>
                      <span v-if="item.unavailableDetail" class="app-unknown__detail">
                        {{ item.unavailableDetail }}
                      </span>
                    </div>
                  </div>
                </div>

                <p class="app-disclaimer">
                  行情取不到时不显示任何推算数字，这是硬性口径：宁可不给，也不给一个会误导下单的数。
                </p>
              </AppGroup>

              <!-- 午盘：持仓相关指数走势 -->
              <AppGroup
                v-show="activePane === 'noon'"
                title="持仓相关指数走势"
                code="TREND"
                :meta="store.trendAsOf ? `日线截至 ${store.trendAsOf}` : '日线未取到'"
                :meta-ok="Boolean(store.trendAsOf)"
              >
                <template #icon>
                  <span v-html="ICONS.trend" />
                </template>

                <div v-if="!store.trends.length" class="app-listempty">
                  尚未取到持仓相关指数的日线，稍后重试或点左下角刷新。
                </div>

                <div v-else class="app-trends">
                  <div v-for="item in store.trends" :key="item.symbol" class="app-trend">
                    <div class="app-trend__head">
                      <b>{{ item.name }}</b>
                      <span class="app-code">{{ item.symbol }}</span>
                      <span class="app-trend__chg" :class="toneClass(item.change)">
                        {{ signedPercent(item.change) }}
                      </span>
                    </div>
                    <AppSparkline :points="item.points" :tone="item.change" />
                    <div class="app-trend__metrics">
                      <span>
                        <i>MA20</i>
                        {{ plain(item.ma20) }}
                      </span>
                      <span>
                        <i>RSI</i>
                        {{ plain(item.rsi, 1) }}
                      </span>
                      <span>
                        <i>MACD</i>
                        {{ plain(item.macdBar, 3) }}
                      </span>
                    </div>
                    <p class="app-trend__note">{{ item.maNote }}</p>
                  </div>
                </div>
              </AppGroup>

              <!-- 午盘：指标状态 -->
              <AppGroup
                v-show="activePane === 'noon'"
                title="技术指标状态"
                code="INDICATOR"
                :meta="indicatorMeta"
                meta-ok
              >
                <template #icon>
                  <span v-html="ICONS.indicator" />
                </template>

                <div class="app-params">
                  <span>RSI 周期 {{ store.payload.indicatorParams.rsiPeriod }}</span>
                  <span>
                    MACD {{ store.payload.indicatorParams.macdFast }}/{{
                      store.payload.indicatorParams.macdSlow
                    }}/{{ store.payload.indicatorParams.macdSignal }}
                  </span>
                </div>

                <div v-if="!store.trends.length" class="app-listempty">
                  日线未取到，指标暂不可算。
                </div>

                <div v-else class="app-indicators">
                  <div v-for="item in store.trends" :key="item.symbol" class="app-indicator">
                    <span class="app-indicator__name">{{ item.name }}</span>
                    <span class="app-indicator__sig" :class="indicatorTone(item)">
                      {{ indicatorText(item) }}
                    </span>
                  </div>
                </div>

                <p class="app-disclaimer">
                  指标由本页按已取到的日线现算，与行情软件可能有细微差异。KDJ
                  需要日线最高价与最低价， 而现有行情接口只回收盘价，故本页不提供 KDJ，待 ISSUE-021
                  补齐日线的开高低价后再加。
                </p>
              </AppGroup>

              <!-- 午盘 V2：两块位置明确显示尚未启用 -->
              <AppGroup
                v-show="activePane === 'noon'"
                title="情绪杀 / 行情杀"
                code="V2"
                meta="V2 尚未启用"
              >
                <template #icon>
                  <span v-html="ICONS.lock" />
                </template>
                <div class="app-pending">
                  <b>尚未启用</b>
                  <p>
                    该区块属 V2
                    范围：只用市场数据（跌幅速度、成交量、涨跌家数、跌停数量、波动率）区分两种下跌成因。
                    当前版本不产出任何结论，位置先占住。
                  </p>
                </div>
              </AppGroup>

              <AppGroup
                v-show="activePane === 'noon'"
                title="资金宏观调配倾向"
                code="V2"
                meta="V2 尚未启用"
              >
                <template #icon>
                  <span v-html="ICONS.lock" />
                </template>
                <div class="app-pending">
                  <b>尚未启用</b>
                  <p>
                    该区块属 V2 范围：给出现金 / 债券 / 股票 /
                    黄金的调配倾向。与上方情绪判断共用同一批行情数据，须同批实现。
                  </p>
                </div>
              </AppGroup>
            </template>
          </div>
        </main>

        <aside class="app-aside">
          <AppAsideBlock title="数据截止">
            <div class="app-source">
              <b>{{ store.showingCache ? '本地缓存' : '本次请求' }}</b>
              补取于 {{ store.dataAsOf }}<br />
              行情日期 {{ store.quoteDate }}
            </div>
          </AppAsideBlock>

          <AppAsideBlock title="今天怎么看">
            <div class="app-mood">
              <span class="app-mood__v">{{ store.marketMood }}</span>
              <p>{{ moodHint }}</p>
            </div>
          </AppAsideBlock>

          <AppAsideBlock title="预估口径">
            <ul class="app-rules">
              <li v-for="(rule, index) in rules" :key="index">{{ rule }}</li>
            </ul>
          </AppAsideBlock>

          <AppAsideBlock title="持仓概览">
            <div class="app-source">
              <b>{{ store.totalCount }} 只持仓</b>
              可预估 {{ store.estimableCount }} 只，不可预估
              {{ store.unavailableItems.length }} 只<br />
              {{
                store.payload.catalog.total
                  ? `基金全集 ${store.payload.catalog.total} 只，更新于 ${shortTime(store.payload.catalog.syncTime)}`
                  : '基金全集未就绪，首次消歧会自动同步'
              }}
            </div>
          </AppAsideBlock>
        </aside>
      </div>
    </div>

    <MessageHost />
  </div>
</template>

<script setup lang="ts">
/**
 * 总览页：早盘看持仓预估与冷暖，午盘看持仓相关指数走势与指标状态，断网时回放本地缓存
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AppAlert from '@/ui/AppAlert.vue'
import AppAsideBlock from '@/layout/AppAsideBlock.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppSideRail from '@/layout/AppSideRail.vue'
import AppSparkline from '@/ui/AppSparkline.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import MessageHost from '@/ui/MessageHost.vue'
import { ElMessage } from '@/ui/message'
import { useOverviewStore } from '@/stores/overview'
import type { TrendItem } from '@/services/overview'
import {
  categoryColor,
  fullTime,
  plain,
  shortTime,
  signedPercent,
  tierColor,
  toneClass,
} from '@/utils/format'

/** 内联图标，保持全站不引图标库 */
const ICONS: Record<string, string> = {
  compass:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="8.6"/><path d="m15.2 8.8-2 4.4-4.4 2 2-4.4z"/></svg>',
  unknown:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="8.6"/><path d="M9.6 9.3a2.5 2.5 0 1 1 3.4 2.3c-.6.3-1 .9-1 1.6v.5M12 16.6v.2"/></svg>',
  trend:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 16.5 8.5 11l3.5 3.2L20.5 6"/><path d="M20.5 11V6h-5"/></svg>',
  indicator:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 17l5-6 4 3 4-7 5 4"/></svg>',
  lock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="4.5" y="10.5" width="15" height="9.5" rx="2.4"/><path d="M8.2 10.5V7.8a3.8 3.8 0 0 1 7.6 0v2.7"/></svg>',
  refresh:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M20 11a8 8 0 1 0-2.3 6.3"/><path d="M20 5v6h-6"/></svg>',
}

/** 全站通用口径，右栏常驻，避免每次都重新解释 */
const rules = [
  '预估基于季度披露的重仓股与指数推算，与实际净值存在误差',
  '股票型 / 场内 ETF / 红利 / 黄金按 0.3 个百分点验收，债券型单独分档',
  '混合型不承诺点估计，只给方向与区间，主动调仓频繁',
  '取不到行情时不显示任何推算数字，宁可空着',
]

const store = useOverviewStore()

/** 当前盘中视角 */
const activePane = ref('morning')

/** 监听上下线的取消函数 */
let stopWatching: (() => void) | null = null

/** 侧栏锚点 */
const railItems = [
  { key: 'morning', label: '早盘总览', icon: ICONS.compass! },
  { key: 'noon', label: '午盘延续', icon: ICONS.trend! },
]

/** 当前失败的环节 */
const failures = computed(() => store.failures)

/** 可预估的基金 */
const items = computed(() => store.items)

/** 不可预估的基金 */
const unavailableItems = computed(() => store.unavailableItems)

/** 参与整体统计的只数 */
const overallCount = computed(() => store.payload.estimate.overallCount ?? 0)

/** 混合型只数 */
const mixedCount = computed(() => store.payload.estimate.mixedCount ?? 0)

/** 页面是否为空状态：没有任何持仓 */
const emptyState = computed(
  () =>
    !store.loading &&
    (store.payload.holding.varietyCount ?? 0) === 0 &&
    items.value.length === 0 &&
    unavailableItems.value.length === 0,
)

/** 数据截止时间，任何时候都显示，否则用户无从判断数据有多旧 */
const dataAsOfLabel = computed(() =>
  store.shownAt ? `${store.stale ? '缓存' : ''}截至 ${store.dataAsOf}` : '',
)

/** 行情条右侧说明 */
const pulseNote = computed(() => {
  if (store.payload.estimate.notice) {
    return store.payload.estimate.notice
  }
  if (store.payload.estimate.degraded) {
    return '有基金走了降级缓存'
  }
  return store.payload.estimate.tradingHours ? '盘中实时行情' : '非交易时段，取上一交易日'
})

/** 预估区块右上角状态 */
const estimateMeta = computed(() => {
  const estimate = store.payload.estimate
  if (estimate.notice) {
    return estimate.notice
  }
  return `可预估 ${store.estimableCount} / ${store.totalCount} 只 · 行情 ${store.quoteDate}`
})

/** 指标区块右上角状态 */
const indicatorMeta = computed(() =>
  store.trendAsOf ? `按 ${store.trendAsOf} 及之前日线现算` : '待日线返回',
)

/** 冷暖的一句话解释 */
const moodHint = computed(() => {
  const changes = store.payload.indexes.map((item) => item.change)
  const usable = changes.filter((value): value is number => value !== null)
  if (!usable.length) {
    return '四个宽基指数都没取到行情，今日不做方向判断。'
  }
  const down = usable.filter((value) => value < 0).length
  if (down === 0) {
    return '宽基全红，按普涨处理，可偏进攻；仍以各自持仓的预估为准。'
  }
  if (down === usable.length) {
    return '宽基全绿，按普跌处理，宜偏防守；先看不可预估区块有没有异常。'
  }
  return '宽基涨跌不一，分歧市，按持仓结构判断而不是看总指数。'
})

/** 方向转成可用于上色的数值 */
function directionTone(item: API.EstimateVO): number | null {
  if (item.direction === 'UP') {
    return 1
  }
  if (item.direction === 'DOWN') {
    return -1
  }
  return 0
}

/** 指标状态文案 */
function indicatorText(item: TrendItem): string {
  if (item.rsi === null) {
    return '样本不足'
  }
  const parts: string[] = []
  if (item.rsi >= 70) {
    parts.push('RSI 超买')
  } else if (item.rsi <= 30) {
    parts.push('RSI 超卖')
  } else {
    parts.push('RSI 中性')
  }
  if (item.macdBar !== null) {
    parts.push(item.macdBar >= 0 ? 'MACD 红柱' : 'MACD 绿柱')
  }
  if (item.ma20 !== null && item.latestClose !== null) {
    parts.push(item.latestClose >= item.ma20 ? '在 MA20 上' : '在 MA20 下')
  }
  return parts.join(' · ')
}

/** 指标状态配色，超买与超卖都提示注意，中性走正文色 */
function indicatorTone(item: TrendItem): string {
  if (item.rsi === null) {
    return 'tone-flat'
  }
  if (item.rsi >= 70 || item.rsi <= 30) {
    return 'tone-warn'
  }
  return 'tone-flat'
}

/** 强制重算预估 */
async function onRefresh(): Promise<void> {
  await store.load(true)
  if (store.failures.length === 0) {
    ElMessage.success('预估已重新计算')
  }
}

onMounted(() => {
  stopWatching = store.trackConnectivity()
  void store.bootstrap()
})

onBeforeUnmount(() => {
  stopWatching?.()
})
</script>

<style scoped>
.app-page {
  min-height: 100vh;
  background: var(--canvas);
  transition: background 0.3s;
}

.app-atmos {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.app-atmos__a,
.app-atmos__b {
  position: absolute;
  filter: blur(22px);
}

.app-atmos__a {
  top: -320px;
  left: -128px;
  width: 960px;
  height: 540px;
  background: radial-gradient(ellipse at center, var(--atmos-a), transparent 72%);
}

.app-atmos__b {
  top: -300px;
  right: -200px;
  width: 840px;
  height: 520px;
  background: radial-gradient(ellipse at center, var(--atmos-b), transparent 72%);
}

.app-body {
  position: relative;
  z-index: 2;
  padding-top: calc(var(--topbar) + var(--pulse));
  padding-left: var(--rail);
  transition: padding-left 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}

.app-stage {
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--aside);
  align-items: start;
}

.app-center {
  min-width: 0;
  padding: 26px 26px 60px;
}

.app-center__in {
  max-width: 880px;
  margin: 0 auto;
}

.app-aside {
  align-self: stretch;
  min-height: calc(100vh - var(--topbar) - var(--pulse));
  padding: 26px 18px 60px;
  border-left: 1px solid var(--hair);
  background: var(--aside-bg);
  transition: background 0.3s;
}

@media (max-width: 1180px) {
  .app-stage {
    grid-template-columns: minmax(0, 1fr);
  }

  .app-aside {
    display: none;
  }
}

@media (max-width: 860px) {
  .app-body {
    padding-top: var(--topbar);
    padding-left: 0;
  }

  .app-center {
    padding: 20px 16px 48px;
  }
}

/* ── 页面头部 ── */
.app-head {
  margin-bottom: 22px;
}

.app-crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--t4);
}

.app-crumb__sl {
  opacity: 0.55;
}

.app-crumb__b {
  font-weight: 500;
  color: var(--t3);
}

.app-eyebrow {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 5px;
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
  color: var(--acc);
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.07em;
  text-transform: uppercase;
}

.app-h1 {
  margin-top: 11px;
  font-size: 28px;
  font-weight: 600;
  line-height: 1.18;
  letter-spacing: -0.033em;
}

.app-lede {
  max-width: 620px;
  margin-top: 9px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--t3);
}

.app-loading {
  padding: 40px 0;
  text-align: center;
  font-size: 12px;
  color: var(--t4);
}

.app-fail {
  display: block;
}

/* ── 空状态 ── */
.app-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 40px 24px;
  border: 1px dashed var(--hair-2);
  border-radius: 15px;
  background: var(--s1);
  text-align: center;
}

.app-empty__ico {
  width: 44px;
  height: 44px;
  color: var(--acc);
}

.app-empty__ico :deep(svg) {
  width: 100%;
  height: 100%;
  stroke: currentColor;
  fill: none;
}

.app-empty b {
  font-size: 15px;
  font-weight: 650;
  color: var(--t1);
}

.app-empty p {
  max-width: 400px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--t3);
}

.app-empty__btn {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 18px;
  border-radius: 7px;
  background: linear-gradient(180deg, var(--acc-2), var(--acc));
  color: var(--acc-on);
  font-size: 12.5px;
  font-weight: 600;
  text-decoration: none;
}

.app-listempty {
  padding: 22px 0;
  text-align: center;
  font-size: 12px;
  line-height: 1.7;
  color: var(--t4);
}

/* ── 预估表 ── */
.app-table {
  padding: 4px 0;
}

.app-table__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--hair-2);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--t4);
}

.app-table__row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--hair);
}

.app-table__row:last-of-type {
  border-bottom: 0;
}

.col-name {
  flex: 1.5;
  min-width: 0;
}

.col-type {
  flex: none;
  width: 56px;
}

.col-change {
  flex: none;
  width: 92px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.col-confidence {
  flex: none;
  width: 78px;
}

.col-note {
  flex: 1.4;
  min-width: 0;
}

.app-name {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--t1);
}

.app-code {
  display: block;
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--t4);
}

.app-tag {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}

.app-change {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.app-direction {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-end;
  font-size: 12.5px;
  font-weight: 600;
}

.app-direction em {
  margin-top: 2px;
  font-size: 10px;
  font-style: normal;
  font-weight: 400;
  color: var(--t3);
}

.app-tier {
  font-size: 10.5px;
  font-weight: 600;
  white-space: nowrap;
}

.app-remark {
  display: block;
  font-size: 10.5px;
  line-height: 1.55;
  color: var(--t3);
}

.app-remark + .app-remark {
  margin-top: 2px;
}

.app-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  padding: 11px 0 6px;
  border-top: 1px solid var(--hair-2);
  font-size: 11.5px;
  color: var(--t2);
}

.app-foot__note {
  color: var(--t4);
}

/* ── 不可预估 ── */
.app-unknown {
  padding: 4px 0;
}

.app-unknown__row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 11px 0;
  border-bottom: 1px solid var(--hair);
}

.app-unknown__row:last-of-type {
  border-bottom: 0;
}

.app-unknown__id {
  flex: 1;
  min-width: 0;
}

.app-unknown__id b {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--t1);
}

.app-unknown__why {
  flex: 1.4;
  min-width: 0;
}

.app-unknown__reason {
  display: block;
  font-size: 11.5px;
  line-height: 1.55;
  color: var(--acc);
}

.app-unknown__detail {
  display: block;
  margin-top: 3px;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--t3);
}

.app-disclaimer {
  margin: 8px 0 6px;
  padding: 9px 12px;
  border-radius: 8px;
  background: var(--s2);
  font-size: 10.5px;
  line-height: 1.6;
  color: var(--t3);
}

/* ── 走势 ── */
.app-trends {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 12px 0 6px;
}

@media (max-width: 720px) {
  .app-trends {
    grid-template-columns: minmax(0, 1fr);
  }
}

.app-trend {
  padding: 12px 14px;
  border: 1px solid var(--hair);
  border-radius: 11px;
  background: var(--inp-bg);
}

.app-trend__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 6px;
}

.app-trend__head b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 600;
  color: var(--t1);
}

.app-trend__chg {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
  font-size: 12.5px;
  font-weight: 650;
}

.app-trend__metrics {
  display: flex;
  gap: 12px;
  margin-top: 6px;
  font-variant-numeric: tabular-nums;
  font-size: 11px;
  color: var(--t2);
}

.app-trend__metrics i {
  margin-right: 3px;
  font-style: normal;
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: var(--t4);
}

.app-trend__note {
  margin-top: 6px;
  font-size: 10.5px;
  color: var(--t3);
}

/* ── 指标 ── */
.app-params {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  padding: 10px 0 6px;
  font-size: 11px;
  color: var(--t4);
}

.app-indicators {
  padding: 4px 0;
}

.app-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--hair);
  font-size: 11.5px;
}

.app-indicator:last-child {
  border-bottom: 0;
}

.app-indicator__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--t1);
}

.app-indicator__sig {
  flex: none;
  font-size: 11px;
}

/* ── V2 占位 ── */
.app-pending {
  padding: 16px 0 10px;
}

.app-pending b {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 5px;
  background: var(--s3);
  font-size: 10.5px;
  font-weight: 650;
  color: var(--t3);
}

.app-pending p {
  max-width: 560px;
  margin-top: 8px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--t4);
}

/* ── 右栏 ── */
.app-source {
  padding: 11px 13px;
  border-radius: 10px;
  background: var(--s2);
  font-size: 11.5px;
  line-height: 1.65;
  color: var(--t3);
}

.app-source b {
  display: block;
  margin-bottom: 3px;
  font-weight: 650;
  color: var(--t1);
}

.app-mood {
  padding: 13px;
  border-radius: 10px;
  background: var(--s2);
}

.app-mood__v {
  display: block;
  font-variant-numeric: tabular-nums;
  font-size: 16px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--t1);
}

.app-mood p {
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.65;
  color: var(--t3);
}

.app-rules {
  padding-left: 15px;
  font-size: 11px;
  line-height: 1.7;
  color: var(--t3);
}

.app-rules li + li {
  margin-top: 5px;
}

/* 涨跌配色，只在本页使用，统一走 token */
.tone-up {
  color: var(--up);
}

.tone-down {
  color: var(--dn);
}

.tone-flat {
  color: var(--t2);
}

.tone-warn {
  color: var(--warn);
}
</style>
