<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <AppTopBar active="review">
      <template #status>
        {{ store.isOffline ? '离线，显示缓存' : store.busy ? '处理中…' : '在线' }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimated-change="null"
      :estimable-count="store.doneRows.length"
      :total-count="store.rows.length"
      :market-mood="store.doneRows.length ? `超阈值 ${store.overRows.length} 只` : '待校准'"
      :data-as-of="dataAsOfLabel"
      :next-note="store.pendingRows.length ? `${store.pendingRows.length} 只待校准` : ''"
    />

    <div class="app-body">
      <AppSideRail
        section-label="复盘视角"
        :items="railItems"
        :active="activePane"
        footer-label="重跑晚间校准"
        :footer-icon="ICONS.refresh"
        @select="activePane = $event"
        @footer="onCalibrate"
      />

      <div class="app-stage">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                复盘 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">{{ store.tradeDate }}</b>
              </div>
              <div class="app-eyebrow">Review</div>
              <h1 class="app-h1">晚间复盘</h1>
              <p class="app-lede">
                用当日实际净值反向检验白天的预估。误差超阈值的基金会标出来，
                供你判断这只基金的预估值后续是否还值得采信。
              </p>
              <div class="app-daybar">
                <label class="app-daybar__lab" for="review-date">复盘日期</label>
                <input
                  id="review-date"
                  :value="store.tradeDate"
                  type="date"
                  class="app-input app-date"
                  @input="onDateChange(($event.target as HTMLInputElement).value)"
                />
                <AppButton size="sm" :disabled="store.busy" @click="onReload">重新读取</AppButton>
                <AppButton size="sm" :disabled="reporting" @click="onExportCompare">
                  {{ reporting ? '导出中…' : '导出对照 CSV' }}
                </AppButton>
                <AppButton size="sm" :disabled="reporting" @click="onExportErrors"
                  >导出误差历史</AppButton
                >
                <AppButton size="sm" :disabled="reporting" @click="onGenerateReport">
                  生成复盘报告
                </AppButton>
                <span v-if="store.calibratedAt !== '—'" class="app-daybar__meta">
                  最近校准 {{ store.calibratedAt }}
                </span>
              </div>
            </div>

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

            <div v-if="store.loading" class="app-loading">正在读取当日误差记录…</div>

            <div v-else-if="!store.rows.length" class="app-empty">
              <div class="app-empty__ico" v-html="ICONS.compass" />
              <b>没有可复盘的持仓</b>
              <p>
                {{
                  store.tradeDate === todayStr
                    ? '当日还没有持仓。录入持仓后，晚间净值公布即可在这里逐只对照。'
                    : '该日期没有持仓记录，也没有对应的误差数据。'
                }}
              </p>
              <RouterLink to="/holding" class="app-empty__btn">前往录入持仓</RouterLink>
            </div>

            <template v-else>
              <!-- 汇总 -->
              <div class="app-summary">
                <div class="app-summary__cell">
                  <div class="app-summary__k">已校准</div>
                  <div class="app-summary__v">
                    {{ store.doneRows.length }}<span> / {{ store.rows.length }}</span>
                  </div>
                </div>
                <div class="app-summary__cell">
                  <div class="app-summary__k">待校准</div>
                  <div class="app-summary__v" :class="{ 'app-summary__v--warn': pendingCount }">
                    {{ pendingCount }}
                  </div>
                </div>
                <div class="app-summary__cell">
                  <div class="app-summary__k">超阈值</div>
                  <div class="app-summary__v" :class="{ 'app-summary__v--bad': overCount }">
                    {{ overCount }}
                  </div>
                </div>
                <div class="app-summary__cell">
                  <div class="app-summary__k">平均绝对误差</div>
                  <div class="app-summary__v">{{ signedAbs(store.meanAbsError) }}</div>
                </div>
                <div class="app-summary__cell">
                  <div class="app-summary__k">方向命中率</div>
                  <div class="app-summary__v">{{ ratio(store.directionAccuracy) }}</div>
                </div>
                <div class="app-summary__cell">
                  <div class="app-summary__k">区间覆盖率</div>
                  <div class="app-summary__v">{{ ratio(store.intervalCoverage) }}</div>
                </div>
              </div>

              <!-- 逐只对照 -->
              <AppGroup
                v-show="activePane === 'compare'"
                title="预估与实际对照"
                code="COMPARE"
                :meta="compareMeta"
                :meta-ok="!overCount"
              >
                <template #icon>
                  <span v-html="ICONS.compare" />
                </template>

                <div class="app-table">
                  <div class="app-table__head">
                    <span class="col-name">基金</span>
                    <span class="col-num">预估</span>
                    <span class="col-num">实际</span>
                    <span class="col-num">误差</span>
                    <span class="col-verdict">判定</span>
                    <span class="col-remark">说明</span>
                  </div>
                  <div
                    v-for="row in store.rows"
                    :key="row.varietyKey"
                    class="app-table__row"
                    :class="{ 'app-table__row--bad': row.overThreshold }"
                  >
                    <div class="col-name">
                      <b class="app-name">{{ row.varietyName ?? row.varietyKey }}</b>
                      <span class="app-code">
                        {{ row.varietyCode ?? row.varietyKey }}
                        <template v-if="row.categoryLabel"> · {{ row.categoryLabel }}</template>
                      </span>
                    </div>
                    <div class="col-num">
                      <span v-if="row.estimated !== null && row.estimated !== undefined">
                        {{ signedPercent(row.estimated) }}
                      </span>
                      <span v-else-if="row.outputTier === 'B'" class="app-muted">方向区间</span>
                      <span v-else-if="row.pending" class="app-muted">—</span>
                      <span v-else class="app-muted">未给数字</span>
                    </div>
                    <div class="col-num">
                      <span
                        v-if="row.actual !== null && row.actual !== undefined"
                        :class="toneClass(row.actual)"
                      >
                        {{ signedPercent(row.actual) }}
                      </span>
                      <span v-else class="app-muted">待公布</span>
                    </div>
                    <div class="col-num">
                      <span
                        v-if="row.error !== null && row.error !== undefined"
                        :class="row.overThreshold ? 'app-error' : ''"
                      >
                        {{ signedPercent(row.error) }}
                      </span>
                      <span v-else class="app-muted">—</span>
                    </div>
                    <div class="col-verdict">
                      <span v-if="row.pending" class="app-pill app-pill--pending">待校准</span>
                      <span v-else-if="row.overThreshold" class="app-pill app-pill--bad"
                        >超阈值</span
                      >
                      <span
                        v-else-if="row.category === 'MIXED'"
                        class="app-pill"
                        :class="mixedPill(row)"
                      >
                        {{ mixedVerdict(row) }}
                      </span>
                      <span v-else class="app-pill app-pill--ok">合格</span>
                    </div>
                    <div class="col-remark">
                      <span v-if="row.pending" class="app-remark app-remark--warn">
                        {{ row.pendingReason ?? store.reasonOf(row.varietyKey) }}
                      </span>
                      <span v-else class="app-remark">
                        阈值 {{ plain(row.threshold) }} 个百分点<template
                          v-if="row.category === 'MIXED'"
                        >
                          · 不参与点估计判定</template
                        >
                      </span>
                      <span v-if="row.pending" class="app-remark app-remark--dim">
                        净值公布后会自动补一次校准，不用手工干预
                      </span>
                    </div>
                  </div>
                </div>

                <div class="app-foot">
                  <span v-if="overCount" class="app-foot__warn">
                    {{ overCount }} 只超阈值，已在上表标红
                  </span>
                  <span v-else-if="store.doneRows.length">全部在阈值内</span>
                  <span v-if="pendingCount" class="app-foot__note">
                    {{ pendingCount }} 只待校准，未参与统计
                  </span>
                  <span v-if="store.payload.notice" class="app-foot__note">
                    {{ store.payload.notice }}
                  </span>
                </div>
              </AppGroup>

              <!-- 停用建议 -->
              <AppGroup
                v-show="activePane === 'compare'"
                title="停用建议"
                code="SUSPEND"
                :meta="
                  store.suspendHints.length
                    ? `${store.suspendHints.length} 只建议停用`
                    : '无建议停用'
                "
                :meta-ok="!store.suspendHints.length"
              >
                <template #icon>
                  <span v-html="ICONS.alert" />
                </template>

                <div v-if="!store.suspendHints.length" class="app-listempty">
                  连续多期超阈值才会出现在这里。当前没有需要停用预估的基金。
                </div>

                <div v-else class="app-hints">
                  <div v-for="item in store.suspendHints" :key="item.varietyKey" class="app-hint">
                    <div class="app-hint__id">
                      <b>{{ item.varietyName ?? item.varietyKey }}</b>
                      <span class="app-code">
                        {{ item.varietyCode }} · {{ item.categoryLabel }}
                      </span>
                    </div>
                    <div class="app-hint__why">
                      <span class="app-hint__bad">
                        连续 {{ item.consecutiveOverCount }} 期超阈值
                      </span>
                      <span class="app-hint__meta">
                        中位误差 {{ plain(item.medianAbsError) }} 个百分点
                        <template v-if="item.mae !== null && item.mae !== undefined">
                          · MAE {{ plain(item.mae) }}
                        </template>
                      </span>
                      <span class="app-hint__tip">
                        该基金的预估值已连续不可信，建议后续不再据此下单；停用开关在 ISSUE-026
                        提供。
                      </span>
                    </div>
                  </div>
                </div>
              </AppGroup>

              <!-- 误差趋势 -->
              <AppGroup
                v-show="activePane === 'trend'"
                title="误差趋势"
                code="TREND"
                meta="点一只基金看近期误差序列"
              >
                <template #icon>
                  <span v-html="ICONS.trend" />
                </template>

                <div class="app-pickers">
                  <button
                    v-for="row in store.doneRows"
                    :key="row.varietyKey"
                    type="button"
                    class="app-picker"
                    :class="{ 'app-picker--on': trendKey === row.varietyKey }"
                    @click="showTrend(row.varietyKey)"
                  >
                    {{ row.varietyName ?? row.varietyKey }}
                  </button>
                </div>

                <div v-if="!store.doneRows.length" class="app-listempty">
                  还没有已校准的基金，无法展示误差趋势。
                </div>

                <div v-else-if="!trendKey" class="app-listempty">
                  选择上方任意一只基金，查看它近期的误差序列。
                </div>

                <div v-else class="app-trendbox">
                  <div class="app-trendbox__head">
                    <b>{{ trendTitle }}</b>
                    <span class="app-code">{{ trendSeries.length }} 期</span>
                  </div>

                  <div v-if="trendLoading" class="app-listempty">读取中…</div>
                  <div v-else-if="trendSeries.length < 2" class="app-listempty">
                    样本不足两期，还画不出趋势。继续每日校准后会自动累积。
                  </div>

                  <template v-else>
                    <AppSparkline :points="trendPoints" :tone="0" />
                    <div class="app-zeroaxis" />
                    <div class="app-trendbox__list">
                      <div
                        v-for="point in trendSeries.slice().reverse()"
                        :key="point.tradeDate"
                        class="app-trendpoint"
                      >
                        <span class="app-trendpoint__d">{{ point.tradeDate }}</span>
                        <span class="app-trendpoint__v">
                          预估 {{ signedPercent(point.estimatedChange) }} · 实际
                          <span :class="toneClass(point.actualChange)">
                            {{ signedPercent(point.actualChange) }}
                          </span>
                        </span>
                        <span
                          class="app-trendpoint__e"
                          :class="point.overThreshold ? 'app-error' : ''"
                        >
                          {{ signedPercent(point.errorValue) }}
                        </span>
                      </div>
                    </div>
                  </template>

                  <p class="app-disclaimer">
                    误差 = 预估 − 实际。折线以零为中线，向上是估高、向下是估低；
                    连续偏离同一侧说明该基金的推算有系统性偏差，可考虑调整跟踪标的。
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
              复盘日期 {{ store.tradeDate }}
            </div>
          </AppAsideBlock>

          <AppAsideBlock title="怎么读这张表">
            <ul class="app-rules">
              <li v-for="(rule, index) in rules" :key="index">{{ rule }}</li>
            </ul>
          </AppAsideBlock>

          <AppAsideBlock title="阈值口径">
            <div class="app-source">
              <b>按类型分档</b>
              股票型 / 场内 ETF / 红利 / 黄金 0.3 个百分点；债券型单独分档，可在参数页改；
              混合型不参与点估计，只看方向命中与区间覆盖。
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
 * 晚间复盘页：逐只展示预估与实际对照、误差标记与近期误差趋势，未出数时显示待校准
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AppAlert from '@/ui/AppAlert.vue'
import AppAsideBlock from '@/layout/AppAsideBlock.vue'
import AppButton from '@/ui/AppButton.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppSideRail from '@/layout/AppSideRail.vue'
import AppSparkline from '@/ui/AppSparkline.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import MessageHost from '@/ui/MessageHost.vue'
import { ElMessage } from '@/ui/message'
import { zeroBaselinePoints } from '@/services/indicators'
import { today } from '@/services/review'
import {
  exportCompare,
  exportErrors,
  generate as generateReport,
} from '@/api/reviewReportController'
import { useReviewStore } from '@/stores/review'
import type { ReviewRow } from '@/stores/review'
import { fullTime, plain, signedPercent, toneClass } from '@/utils/format'

/** 内联图标，保持全站不引图标库 */
const ICONS: Record<string, string> = {
  compass:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="8.6"/><path d="m15.2 8.8-2 4.4-4.4 2 2-4.4z"/></svg>',
  compare:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3.5v17M7 8 3.5 11.5 7 15M17 8l3.5 3.5L17 15"/></svg>',
  alert:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4.5 2.8 20h18.4z"/><path d="M12 10v4.2M12 17.2v.2"/></svg>',
  trend:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 16.5 8.5 11l3.5 3.2L20.5 6"/><path d="M20.5 11V6h-5"/></svg>',
  refresh:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M20 11a8 8 0 1 0-2.3 6.3"/><path d="M20 5v6h-6"/></svg>',
}

/** 读表口径，右栏常驻 */
const rules = [
  '误差 = 预估 − 实际，正数是估高了，负数是估低了',
  '超阈值只说明这一次不准，连续多期超标才会进入停用建议',
  '待校准不是出错：晚间净值还没公布时本来就没有可对照的数字',
  '混合型看方向与区间，不给单一误差值',
]

/** 今天的日期，用于判断空状态文案 */
const todayStr = today()

const store = useReviewStore()

/** 当前视角 */
const activePane = ref('compare')

/** 当前选中的趋势基金 */
const trendKey = ref('')

/** 趋势读取中 */
const trendLoading = ref(false)

/** 趋势序列 */
const trendSeries = ref<API.EstimateErrorVO[]>([])

/** 报告导出中 */
const reporting = ref(false)

/** 监听上下线的取消函数 */
let stopWatching: (() => void) | null = null

/** 侧栏锚点 */
const railItems = [
  { key: 'compare', label: '预估对照', icon: ICONS.compare! },
  { key: 'trend', label: '误差趋势', icon: ICONS.trend! },
]

/** 失败环节 */
const failures = computed(() => store.failures)

/** 待校准只数 */
const pendingCount = computed(() => store.pendingRows.length)

/** 超阈值只数 */
const overCount = computed(() => store.overRows.length)

/** 数据截止时间 */
const dataAsOfLabel = computed(() =>
  store.shownAt ? `${store.stale ? '缓存' : ''}截至 ${store.dataAsOf}` : '',
)

/** 对照区右上角状态 */
const compareMeta = computed(() => {
  if (!store.rows.length) {
    return '当日无记录'
  }
  return `已校准 ${store.doneRows.length} / ${store.rows.length} 只 · ${store.tradeDate}`
})

/** 趋势标题 */
const trendTitle = computed(() => {
  const row = store.rows.find((item) => item.varietyKey === trendKey.value)
  return row?.varietyName ?? trendKey.value
})

/** 趋势折线的坐标串，以误差中线的零点为基准 */
const trendPoints = computed(() =>
  zeroBaselinePoints(
    trendSeries.value
      .map((point) => point.errorValue)
      .filter((value): value is number => value !== null && value !== undefined),
  ),
)

/** 平均绝对误差展示 */
function signedAbs(value: number | null): string {
  if (value === null) {
    return '—'
  }
  return `${value.toFixed(4)}`
}

/** 比率展示 */
function ratio(value: number | null): string {
  if (value === null) {
    return '—'
  }
  return `${value.toFixed(1)}%`
}

/** 混合型方向与区间的判定文案 */
function mixedVerdict(row: ReviewRow): string {
  if (row.directionHit && row.inInterval) {
    return '方向命中且落区间'
  }
  if (row.directionHit) {
    return '方向命中 · 出区间'
  }
  if (row.inInterval) {
    return '方向未中 · 落区间'
  }
  return '方向未中且出区间'
}

/** 混合型判定的配色 */
function mixedPill(row: ReviewRow): string {
  return row.directionHit || row.inInterval ? 'app-pill--ok' : 'app-pill--bad'
}

/** 切换日期 */
function onDateChange(value: string): void {
  if (!value) {
    return
  }
  store.setTradeDate(value)
  trendKey.value = ''
  trendSeries.value = []
  void store.load()
}

/** 重新读取 */
async function onReload(): Promise<void> {
  await store.load(true)
  if (!store.failures.length) {
    ElMessage.success('已重新读取')
  }
}

/** 手动重跑晚间校准 */
async function onCalibrate(): Promise<void> {
  try {
    const result = await store.calibrate()
    const gapCount = result.gapCount ?? 0
    ElMessage.success(
      `校准完成：${result.calibratedCount ?? 0} 只，其中超阈值 ${result.overThresholdCount ?? 0} 只` +
        (gapCount ? `，${gapCount} 只待净值公布` : ''),
    )
  } catch {
    // request 层已弹出具体原因
  }
}

/**
 * 导出当日对照 CSV，文件落在服务器的 data/report 下，页面只回路径
 *
 * @returns 无
 */
async function onExportCompare(): Promise<void> {
  reporting.value = true
  try {
    const result = await exportCompare({ tradeDate: store.tradeDate })
    const data = result.data
    if (!data?.savedPath) {
      ElMessage.error((data?.notes ?? []).join('；') || '导出失败，未返回文件路径')
      return
    }
    ElMessage.success(`已导出 ${data.rows?.length ?? 0} 行到 ${data.savedPath}`)
  } catch {
    // request 层已弹出具体原因
  } finally {
    reporting.value = false
  }
}

/**
 * 导出误差历史 CSV，区间取当前复盘日期往前 30 天
 *
 * @returns 无
 */
async function onExportErrors(): Promise<void> {
  reporting.value = true
  try {
    const end = new Date(store.tradeDate)
    const start = new Date(end)
    start.setDate(start.getDate() - 30)
    const result = await exportErrors({
      startDate: start.toISOString().slice(0, 10),
      endDate: end.toISOString().slice(0, 10),
    })
    ElMessage.success(result.data ?? '已导出')
  } catch {
    // request 层已弹出具体原因
  } finally {
    reporting.value = false
  }
}

/**
 * 生成当日复盘报告并留存在 data/report 下，供归档
 *
 * @returns 无
 */
async function onGenerateReport(): Promise<void> {
  reporting.value = true
  try {
    const result = await generateReport({ tradeDate: store.tradeDate })
    const data = result.data
    if (!data?.savedPath) {
      ElMessage.error('报告生成失败，未返回留存路径')
      return
    }
    ElMessage.success(`报告已留存到 ${data.savedPath}`)
  } catch {
    // request 层已弹出具体原因
  } finally {
    reporting.value = false
  }
}

/** 展示某只基金的误差趋势 */
async function showTrend(varietyKey: string): Promise<void> {
  trendKey.value = varietyKey
  trendLoading.value = true
  try {
    trendSeries.value = await store.trendOf(varietyKey)
  } catch {
    trendSeries.value = []
  } finally {
    trendLoading.value = false
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
  max-width: var(--content);
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

/* ── 头部 ── */
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

.app-daybar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 15px;
}

.app-daybar__lab {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--t4);
}

.app-daybar__meta {
  font-size: 11px;
  color: var(--t4);
}

.app-date {
  flex: none;
  max-width: 168px;
  font-variant-numeric: tabular-nums;
}

.app-date::-webkit-calendar-picker-indicator {
  filter: invert(var(--cal-invert, 0));
  cursor: pointer;
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
  max-width: 420px;
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
  padding: 20px 0;
  text-align: center;
  font-size: 12px;
  line-height: 1.7;
  color: var(--t4);
}

/* ── 汇总 ── */
.app-summary {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 1px;
  margin-bottom: 14px;
  overflow: hidden;
  border: 1px solid var(--hair);
  border-radius: 13px;
  background: var(--hair);
}

@media (max-width: 720px) {
  .app-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.app-summary__cell {
  padding: 12px 14px;
  background: var(--s1);
}

.app-summary__k {
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--t4);
}

.app-summary__v {
  margin-top: 4px;
  font-variant-numeric: tabular-nums;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--t1);
}

.app-summary__v span {
  font-size: 11px;
  font-weight: 400;
  color: var(--t4);
}

.app-summary__v--warn {
  color: var(--warn);
}

.app-summary__v--bad {
  color: var(--acc);
}

/* ── 对照表 ── */
.app-table {
  padding: 4px 0;
}

.app-table__head {
  display: flex;
  align-items: center;
  gap: 10px;
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
  gap: 10px;
  padding: 11px 0;
  border-bottom: 1px solid var(--hair);
}

.app-table__row:last-of-type {
  border-bottom: 0;
}

/* 超阈值整行加左侧强调条，一眼可分 */
.app-table__row--bad {
  padding-left: 8px;
  border-left: 2px solid var(--acc);
  background: linear-gradient(90deg, var(--acc-soft), transparent 60%);
}

.col-name {
  flex: 1.4;
  min-width: 0;
}

.col-num {
  flex: none;
  width: 74px;
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  color: var(--t2);
}

.col-verdict {
  flex: none;
  width: 108px;
}

.col-remark {
  flex: 1.2;
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

.app-muted {
  font-size: 11px;
  color: var(--t4);
}

.app-error {
  color: var(--acc);
  font-weight: 600;
}

.app-pill {
  display: inline-flex;
  align-items: center;
  height: 19px;
  padding: 0 7px;
  border-radius: 4px;
  background: var(--s3);
  color: var(--t3);
  font-size: 10px;
  font-weight: 650;
  white-space: nowrap;
}

.app-pill--ok {
  background: var(--dn-soft);
  color: var(--dn);
}

.app-pill--bad {
  background: var(--acc-soft);
  color: var(--acc);
}

.app-pill--pending {
  background: color-mix(in oklab, var(--warn) 14%, transparent);
  color: var(--warn);
}

.app-remark {
  display: block;
  font-size: 10.5px;
  line-height: 1.55;
  color: var(--t3);
}

.app-remark--warn {
  color: var(--warn);
}

.app-remark--dim {
  margin-top: 2px;
  color: var(--t4);
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

.app-foot__warn {
  font-weight: 600;
  color: var(--acc);
}

.app-foot__note {
  color: var(--t4);
}

/* ── 停用建议 ── */
.app-hints {
  padding: 4px 0;
}

.app-hint {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid var(--hair);
}

.app-hint:last-of-type {
  border-bottom: 0;
}

.app-hint__id {
  flex: 1;
  min-width: 0;
}

.app-hint__id b {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--t1);
}

.app-hint__why {
  flex: 1.6;
  min-width: 0;
}

.app-hint__bad {
  display: block;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--acc);
}

.app-hint__meta {
  display: block;
  margin-top: 3px;
  font-variant-numeric: tabular-nums;
  font-size: 10.5px;
  color: var(--t3);
}

.app-hint__tip {
  display: block;
  margin-top: 3px;
  font-size: 10.5px;
  line-height: 1.55;
  color: var(--t4);
}

/* ── 趋势 ── */
.app-pickers {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  padding: 12px 0 8px;
}

.app-picker {
  height: 27px;
  padding: 0 11px;
  border: 1px solid var(--hair-2);
  border-radius: 7px;
  background: var(--inp-bg);
  color: var(--t2);
  font: inherit;
  font-size: 11.5px;
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.app-picker:hover {
  border-color: var(--hair-3);
}

.app-picker--on {
  border-color: var(--acc);
  box-shadow: 0 0 0 3px var(--acc-glow);
  color: var(--acc);
  font-weight: 600;
}

.app-trendbox {
  padding: 12px 0 6px;
}

.app-trendbox__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}

.app-trendbox__head b {
  font-size: 13px;
  font-weight: 600;
  color: var(--t1);
}

.app-zeroaxis {
  height: 1px;
  margin-top: -15px;
  margin-bottom: 14px;
  background: var(--hair-2);
}

.app-trendbox__list {
  padding-top: 4px;
}

.app-trendpoint {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 7px 0;
  border-bottom: 1px solid var(--hair);
  font-size: 11px;
}

.app-trendpoint:last-child {
  border-bottom: 0;
}

.app-trendpoint__d {
  flex: none;
  width: 88px;
  font-variant-numeric: tabular-nums;
  color: var(--t4);
}

.app-trendpoint__v {
  flex: 1;
  min-width: 0;
  font-variant-numeric: tabular-nums;
  color: var(--t2);
}

.app-trendpoint__e {
  flex: none;
  width: 70px;
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
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

.app-rules {
  padding-left: 15px;
  font-size: 11px;
  line-height: 1.7;
  color: var(--t3);
}

.app-rules li + li {
  margin-top: 5px;
}

/* 涨跌配色，统一走 token */
.tone-up {
  color: var(--up);
}

.tone-down {
  color: var(--dn);
}

.tone-flat {
  color: var(--t2);
}
</style>
