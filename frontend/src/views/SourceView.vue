<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <AppTopBar active="source">
      <template #status>
        {{ health?.hasTrouble ? '有源异常' : '全部正常' }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimable-count="healthyCount"
      :total-count="rows.length"
      :market-mood="health?.hasTrouble ? '有源失败率偏高' : '降级链路正常'"
      :data-as-of="rangeLabel"
      :next-note="health?.note ?? ''"
    />

    <div class="app-body">
      <div class="app-stage app-stage--no-aside">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                运维 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">数据源</b>
              </div>
              <div class="app-eyebrow">Sources</div>
              <h1 class="app-h1">数据源健康度</h1>
              <p class="app-lede">
                免费数据源改版是这套工具的头号风险。这里按成功与失败分别统计，并把失败拆成
                结构变化、被限流、网络失败、无数据四类——连续出现「结构变化」就说明上游改版了。
              </p>
              <div class="app-daybar">
                <label class="app-daybar__lab" for="src-from">统计区间</label>
                <input id="src-from" v-model="startDate" type="date" class="app-date" />
                <span class="app-daybar__sep">至</span>
                <input v-model="endDate" type="date" class="app-date" />
                <AppButton size="sm" :disabled="loading" @click="load">重新统计</AppButton>
              </div>
            </div>

            <div v-if="loading" class="app-loading">正在汇总取数记录…</div>

            <template v-else>
              <AppAlert v-if="errorText" kind="error">{{ errorText }}</AppAlert>

              <AppAlert v-if="health?.note" :kind="health?.hasTrouble ? 'warn' : 'info'">
                {{ health.note }}
              </AppAlert>

              <AppAlert kind="info" title="失败率怎么读">
                失败率只统计「结构变化 / 被限流 / 网络失败」这类源自身的问题；
                「无数据」是标的本身取不到（指数在另一个市场、停牌、场外基金没有日线），
                单独计数，不进入失败率。备用源只在前一个源取不到时才被调用， 所以它的样本天然偏少。
              </AppAlert>

              <AppGroup
                title="各源成败"
                code="STATS"
                :meta="rows.length ? `${rows.length} 个源` : '无记录'"
                :meta-ok="!health?.hasTrouble"
              >
                <template #icon>
                  <span v-html="ICONS.pulse" />
                </template>

                <div v-if="!rows.length" class="app-listempty">
                  统计区间内没有取数记录。可能是留痕开关关闭
                  （`fund.adapter.probe.enabled`），或应用刚启动还没取过数。
                </div>

                <div v-else class="app-src">
                  <div
                    v-for="row in rows"
                    :key="row.source"
                    class="app-src__row"
                    :class="{ 'app-src__row--bad': trouble(row) }"
                  >
                    <div class="app-src__name">
                      <b>{{ row.source }}</b>
                      <span class="app-src__sub">{{ purposeOf(row.source) }}</span>
                    </div>
                    <div class="app-src__nums">
                      <span>{{ row.totalCount ?? 0 }} 次</span>
                      <span class="app-src__sub">
                        成功 {{ row.successCount ?? 0 }} · 真失败 {{ row.failureCount ?? 0 }} ·
                        无数据 {{ row.noDataCount ?? 0 }} · 失败率 {{ pct(row.failureRate) }}
                      </span>
                    </div>
                    <div class="app-src__state">
                      <span v-if="trouble(row)" class="app-src__badge">失败率偏高</span>
                      <span
                        v-else-if="(row.failureCount ?? 0) > 0"
                        class="app-src__badge app-src__badge--mild"
                      >
                        有失败
                      </span>
                      <span v-else class="app-src__badge app-src__badge--ok">正常</span>
                    </div>
                    <ul v-if="row.failures?.length" class="app-src__why">
                      <li v-for="kind in row.failures" :key="kind">{{ kindText(kind) }}</li>
                    </ul>
                    <div v-if="row.failureTargets?.length" class="app-src__targets">
                      <div
                        v-for="target in row.failureTargets"
                        :key="target.target"
                        class="app-src__target"
                      >
                        <code>{{ target.target }}</code>
                        <span>{{ target.count }} 次 · {{ kindText(target.failureKind) }}</span>
                        <span v-if="target.latestMessage" class="app-src__msg">
                          {{ target.latestMessage }}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </AppGroup>

              <AppGroup title="降级矩阵" code="MATRIX" meta="每个源失效时的影响">
                <template #icon>
                  <span v-html="ICONS.grid" />
                </template>

                <div class="app-matrix">
                  <div
                    v-for="row in health?.matrix ?? []"
                    :key="row.source"
                    class="app-matrix__row"
                  >
                    <div class="app-matrix__head">
                      <b>{{ row.source }}</b>
                      <span
                        class="app-matrix__fb"
                        :class="row.hasFallback ? 'app-matrix__fb--ok' : 'app-matrix__fb--none'"
                      >
                        {{ row.hasFallback ? '有备用源' : '无备用源' }}
                      </span>
                    </div>
                    <p class="app-matrix__purpose">{{ row.purpose }}</p>
                    <p class="app-matrix__affect">
                      受影响：{{ (row.affected ?? []).join('、') || '无' }}
                    </p>
                    <p class="app-matrix__notice">{{ row.notice }}</p>
                  </div>
                </div>

                <p class="app-hint">
                  矩阵与后端 `DegradationMatrix` 同源，改代码时以代码为准。无备用源的三个
                  （eastmoney / csindex / smtp）是真正需要盯的。
                </p>
              </AppGroup>
            </template>
          </div>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 数据源健康度页：逐源统计成败与失败类型，并展示降级矩阵
 */
import { computed, onMounted, ref } from 'vue'
import AppAlert from '@/ui/AppAlert.vue'
import AppButton from '@/ui/AppButton.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import { health as sourceHealth } from '@/api/sourceHealthController'

/** 内联图标 */
const ICONS: Record<string, string> = {
  pulse:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12h3l2.5-7 3.5 14 3-7h6"/></svg>',
  grid: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7.5" height="7.5" rx="1.6"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="1.6"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="1.6"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="1.6"/></svg>',
}

/**
 * 失败率阈值，与后端 SourceHealthServiceImpl.TROUBLE_RATE 一致
 */
const TROUBLE_RATE = 30

/** 样本量下限，与后端 MIN_SAMPLES 一致 */
const MIN_SAMPLES = 5

const loading = ref(true)
const errorText = ref('')
const startDate = ref(isoOf(-7))
const endDate = ref(isoOf(0))

/**
 * 数据源统计的补充字段：无数据次数与失败标的明细是本轮新增，
 * 生成层尚未更新，故在此扩展；接口重新生成后可删掉。
 */
type SourceStatDisplay = API.SourceStatVO & {
  noDataCount?: number
  failureTargets?: Array<{
    source?: string
    target?: string
    failureKind?: string
    count?: number
    latestMessage?: string
    latestAt?: string
  }>
}

const health = ref<(API.SourceHealthVO & { sources?: SourceStatDisplay[] }) | null>(null)

/** 各源统计 */
const rows = computed(() => health.value?.sources ?? [])

/** 未超阈值的源数 */
const healthyCount = computed(() => rows.value.filter((row) => !trouble(row)).length)

/** 统计区间文字 */
const rangeLabel = computed(() =>
  health.value?.startDate && health.value?.endDate
    ? `${health.value.startDate} ~ ${health.value.endDate}`
    : '',
)

/**
 * 生成相对今天偏移的 ISO 日期，供日期输入框默认值
 *
 * @param offsetDays 偏移天数，负数表示往前
 * @returns yyyy-MM-dd
 */
function isoOf(offsetDays: number): string {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  return date.toISOString().slice(0, 10)
}

/**
 * 取健康度
 *
 * @returns 无
 */
async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const result = await sourceHealth({ startDate: startDate.value, endDate: endDate.value })
    health.value = result.data ?? null
  } catch {
    errorText.value = '健康度读取失败，请确认已登录'
    health.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 是否判为失败率偏高，样本不足时不下结论
 *
 * @param row 统计
 * @returns 偏高返回 true
 */
function trouble(row: API.SourceStatVO): boolean {
  if ((row.totalCount ?? 0) < MIN_SAMPLES) {
    return false
  }
  return (row.failureRate ?? 0) > TROUBLE_RATE
}

/**
 * 数据源用途，从降级矩阵里取，取不到给空
 *
 * @param source 数据源名
 * @returns 用途
 */
function purposeOf(source?: string): string {
  const row = (health.value?.matrix ?? []).find((item) => item.source === source)
  return row?.purpose ?? ''
}

/**
 * 失败类型的中文说明
 *
 * @param kind 类型代码
 * @returns 说明
 */
function kindText(kind?: string): string {
  switch (kind) {
    case 'STRUCTURE':
      return '结构变化（关键字段缺失，上游可能改版了）'
    case 'THROTTLED':
      return '被限流（返回验证页或拒绝访问）'
    case 'NETWORK':
      return '网络失败（重试通常可恢复）'
    case 'NO_DATA':
      return '无数据（结构正常，标的不存在或停牌）'
    default:
      return kind ?? '未知'
  }
}

/**
 * 百分比展示，保留两位
 *
 * @param value 数值
 * @returns 文本
 */
function pct(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return `${Number(value).toFixed(2)}%`
}

onMounted(load)
</script>

<style scoped>
/* 骨架类与通用提示（app-daybar / app-listempty / app-hint 等）来自全局 styles/layout.css */
.app-src {
  margin: 0;
}

.app-matrix {
  margin: 0;
}

.app-src__row {
  padding: 10px 0;
  border-bottom: 1px solid var(--hair);
}

.app-src__row:last-child {
  border-bottom: none;
}

.app-src__row--bad {
  background: linear-gradient(90deg, transparent, var(--dn-soft), transparent);
}

.app-src__name {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
  color: var(--t1);
}

.app-src__sub {
  font-size: 12px;
  color: var(--t3);
}

.app-src__nums {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--t2);
}

.app-src__state {
  margin-top: 4px;
}

.app-src__badge {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11.5px;
  background: var(--dn-soft);
  color: var(--dn);
}

.app-src__badge--mild {
  background: var(--acc-soft);
  color: var(--acc);
}

.app-src__badge--ok {
  background: var(--group-bg);
  color: var(--t3);
}

.app-src__why {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--t2);
}

.app-src__targets {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
  padding: 8px 12px;
  border-radius: 9px;
  background: var(--s2);
}

.app-src__target {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 11.5px;
  color: var(--t2);
}

.app-src__target code {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--t1);
}

.app-src__msg {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--t4);
}

.app-matrix__row {
  padding: 10px 0;
  border-bottom: 1px solid var(--hair);
}

.app-matrix__row:last-child {
  border-bottom: none;
}

.app-matrix__head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--t1);
}

.app-matrix__fb {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11.5px;
}

.app-matrix__fb--ok {
  background: var(--acc-soft);
  color: var(--acc);
}

.app-matrix__fb--none {
  background: var(--dn-soft);
  color: var(--dn);
}

.app-matrix__purpose {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--t2);
}

.app-matrix__affect {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--t3);
}

.app-matrix__notice {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--t2);
}

.app-daybar__sep {
  font-size: 12px;
  color: var(--t3);
}
</style>
