<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <AppTopBar active="task">
      <template #status>
        {{ overview?.tradingDay === false ? '休市' : loading ? '读取中…' : '已就绪' }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimable-count="overview?.sentCount ?? 0"
      :total-count="overview?.items?.length ?? 4"
      :market-mood="moodLabel"
      :next-note="overview?.notice ?? ''"
    />

    <div class="app-body">
      <div class="app-stage app-stage--no-aside">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                运维 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">调度</b>
              </div>
              <div class="app-eyebrow">Schedule</div>
              <h1 class="app-h1">四时点调度</h1>
              <p class="app-lede">
                这套工具每天在四个时刻自动产出：09:30 刷新早盘预估、11:30 刷新午盘、 14:30
                发收盘前邮件、20:00 跑晚间校准。本页展示每个时刻今天做了没有、
                失败在哪，并提供手动触发与补跑。
              </p>
              <div class="app-daybar">
                <label class="app-daybar__lab" for="task-date">查看日期</label>
                <input
                  id="task-date"
                  :value="queryDate"
                  type="date"
                  class="app-input app-date"
                  @input="onDateChange(($event.target as HTMLInputElement).value)"
                />
                <AppButton size="sm" :disabled="busy" @click="onCatchUp">立即补跑</AppButton>
                <AppButton size="sm" :disabled="busy" @click="load">重新读取</AppButton>
                <span class="app-daybar__meta">
                  补跑只补「已过应发时刻仍未成功」且在时效窗口内的时点
                </span>
              </div>
            </div>

            <AppAlert v-if="overview?.tradingDay === false" kind="info" title="非交易日">
              {{ overview?.tradingDayNote ?? '当天休市，四个时点均不触发' }}
            </AppAlert>
            <AppAlert
              v-if="overview?.clockJump"
              kind="warn"
              :title="`检测到时钟跳变 ${overview?.clockJumpSeconds ?? 0} 秒`"
            >
              跳变保护期内的触发一律跳过并留痕，避免把错误的时刻当成漏跑。
            </AppAlert>

            <div v-if="loading && !overview" class="app-loading">正在读取调度状态…</div>

            <template v-else>
              <AppGroup
                v-for="item in overview?.items ?? []"
                :key="item.point"
                :title="`${item.pointLabel} ${item.dueTime ?? ''}`"
                :code="item.point ?? ''"
                :meta="metaOf(item)"
                :meta-ok="item.status === 'SENT' || item.status === 'RESENT'"
              >
                <template #icon>
                  <span v-html="iconOf(item.point)" />
                </template>

                <div class="app-point">
                  <div class="app-point__desc">{{ PURPOSES[item.point ?? ''] ?? '' }}</div>
                  <div class="app-point__row">
                    <span class="app-pill" :class="pillClass(item)">
                      {{ item.statusLabel ?? '未发送' }}
                    </span>
                    <span v-if="item.sentTime" class="app-point__time">
                      {{ shortTime(item.sentTime) }}
                    </span>
                    <span v-if="item.overdue" class="app-pill app-pill--bad">已过期未成功</span>
                  </div>
                  <p v-if="item.failReason" class="app-point__fail">
                    失败原因：{{ item.failReason }}
                  </p>
                  <p v-if="item.contentPath" class="app-point__path">
                    内容留存：{{ item.contentPath }}
                  </p>
                  <div class="app-point__ops">
                    <AppButton size="sm" :disabled="busy" @click="onTrigger(item, false)">
                      手动触发
                    </AppButton>
                    <AppButton
                      v-if="item.point === 'PRE_CLOSE'"
                      size="sm"
                      :disabled="busy"
                      @click="onTrigger(item, true)"
                    >
                      补发未送达
                    </AppButton>
                  </div>
                </div>
              </AppGroup>

              <AppGroup title="邮件留存" code="MAIL" :meta="mailMeta" :meta-ok="!mailPendingCount">
                <template #icon>
                  <span v-html="ICONS.mail" />
                </template>

                <p class="app-point__desc">
                  每封邮件发出前后都会把正文写到本地 <code>data/mail</code> 目录： 文件名带
                  <code>sent</code> 的是成功件，带 <code>failed</code>
                  的是发送失败件。失败件会在下一次成功发送时自动附在补发段里重发一遍。
                </p>
                <p class="app-point__dir">留存目录：{{ overview?.mailArchiveDir ?? '—' }}</p>
                <div v-if="!mailArchives.length" class="app-listempty">
                  本地还没有邮件留存文件。
                </div>
                <div v-else class="app-mails">
                  <div
                    v-for="mail in mailArchives"
                    :key="mail.fileName"
                    class="app-mail"
                    :class="{ 'app-mail--bad': mail.failed }"
                  >
                    <span class="app-mail__tag" :class="mail.failed ? 'app-mail__tag--bad' : ''">
                      {{ mail.failed ? '失败' : '已发送' }}
                    </span>
                    <div class="app-mail__tx">
                      <b>{{ mail.subject }}</b>
                      <span>
                        {{ mail.tradeDate }} · {{ mail.sentAt ?? mailArchivedAt(mail) }}
                        <template v-if="mail.resent"> · 已补发过</template>
                      </span>
                    </div>
                    <span class="app-mail__file">{{ mail.fileName }}</span>
                  </div>
                </div>
              </AppGroup>

              <AppGroup
                title="发送留痕"
                code="RECORDS"
                :meta="`${records.length} 条`"
                :meta-ok="true"
              >
                <template #icon>
                  <span v-html="ICONS.list" />
                </template>

                <div v-if="!records.length" class="app-listempty">最近 14 天没有发送留痕。</div>
                <div v-else class="app-records">
                  <div v-for="row in records" :key="row.id" class="app-record">
                    <span class="app-record__date">{{ row.sendDate }}</span>
                    <span class="app-record__point">{{ pointLabel(row.sendPoint) }}</span>
                    <span class="app-pill" :class="recordPill(row.status)">
                      {{ statusLabel(row.status) }}
                    </span>
                    <span class="app-record__time">
                      {{ row.sentTime ? shortTime(row.sentTime) : '—' }}
                    </span>
                    <span v-if="row.failReason" class="app-record__why" :title="row.failReason">
                      {{ row.failReason }}
                    </span>
                  </div>
                </div>
              </AppGroup>
            </template>
          </div>
        </main>
      </div>
    </div>

    <MessageHost />
  </div>
</template>

<script setup lang="ts">
/**
 * 调度页：展示四个时点的今日状态、失败原因与邮件留存，支持手动触发与补跑
 */
import { computed, onMounted, ref } from 'vue'
import AppAlert from '@/ui/AppAlert.vue'
import AppButton from '@/ui/AppButton.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import MessageHost from '@/ui/MessageHost.vue'
import { ElMessage } from '@/ui/message'
import {
  catchUp,
  overview as taskOverview,
  records as taskRecords,
  trigger,
} from '@/api/taskController'

/**
 * 时点状态与邮件留存是本轮新增字段，生成层尚未更新，
 * 在此声明补充结构；接口重新生成后可删掉这组本地类型。
 */
type TaskPointStatusVO = API.TaskPointStatusVO

interface TaskOverviewDisplay {
  tradeDate?: string
  tradingDay?: boolean
  tradingDayNote?: string
  items?: TaskPointStatusVO[]
  sentCount?: number
  failedCount?: number
  overdueCount?: number
  clockJump?: boolean
  clockJumpSeconds?: number
  notice?: string
  mailArchiveDir?: string
  mailPendingResendCount?: number
  mailArchives?: MailArchiveDisplay[]
}

/** 一份本地邮件留存 */
interface MailArchiveDisplay {
  fileName?: string
  subject?: string
  tradeDate?: string
  sentAt?: string
  failed?: boolean
  resent?: boolean
  sizeBytes?: number
  archivedAt?: string
}

/** 四个时点各自是干嘛的一句话说明 */
const PURPOSES: Record<string, string> = {
  MORNING: '刷新一次全量预估，让页面一打开就有当日数字',
  NOON: '复用缓存刷新午盘预估，缓存过期才重算',
  PRE_CLOSE: '收盘前 30 分钟发提醒邮件，含预估、建议与警示',
  EVENING: '跑晚间校准，把白天预估与实际净值逐只对照并写误差历史',
}

/** 内联图标 */
const ICONS: Record<string, string> = {
  morning:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="4"/><path d="M12 2.8v2.4M12 18.8v2.4M4.8 12H2.4M21.6 12h-2.4M6.6 6.6 5 5M19 19l-1.6-1.6M6.6 17.4 5 19M19 5l-1.6 1.6"/></svg>',
  noon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="13" r="4.2"/><path d="M12 4v2.2M4.5 13H2.2M21.8 13h-2.3M17.8 8.2l1.6-1.6M4.6 6.6l1.6 1.6"/></svg>',
  preClose:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8.5h18l-8.6 5.2a1.2 1.2 0 0 1-1.2 0z"/><path d="M4.5 8.5v9a1.5 1.5 0 0 0 1.5 1.5h12a1.5 1.5 0 0 0 1.5-1.5v-9"/></svg>',
  evening:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M20.2 14.5A8.4 8.4 0 1 1 9.6 3.8a6.8 6.8 0 1 0 10.6 10.7z"/></svg>',
  mail: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5.5" width="18" height="13" rx="2"/><path d="m3.5 7 8.5 6 8.5-6"/></svg>',
  list: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01"/></svg>',
}

/** 读取中 */
const loading = ref(true)

/** 触发或补跑中 */
const busy = ref(false)

/** 查询日期，默认今天 */
const queryDate = ref(today())

/** 总览 */
const overview = ref<TaskOverviewDisplay | null>(null)

/** 最近 14 天留痕 */
const records = ref<API.SendRecordVO[]>([])

/** 顶部状态文案 */
const moodLabel = computed(() => {
  if (!overview.value) {
    return '—'
  }
  const failed = overview.value.failedCount ?? 0
  const overdue = overview.value.overdueCount ?? 0
  if (overview.value.tradingDay === false) {
    return '休市'
  }
  if (failed || overdue) {
    return `失败 ${failed} · 过期 ${overdue}`
  }
  return `已成功 ${overview.value.sentCount ?? 0} / ${overview.value.items?.length ?? 4}`
})

/** 未补发的失败件数量 */
const mailPendingCount = computed(() => overview.value?.mailPendingResendCount ?? 0)

/** 邮件留存清单 */
const mailArchives = computed(() => overview.value?.mailArchives ?? [])

/** 邮件分组右上角状态 */
const mailMeta = computed(() =>
  mailPendingCount.value ? `${mailPendingCount.value} 封失败件待补发` : '无待补发失败件',
)

/** 时点分组右上角状态 */
function metaOf(item: TaskPointStatusVO): string {
  if (item.failReason) {
    return '失败，原因见下方'
  }
  if (item.overdue) {
    return '已过应发时刻，可手动触发'
  }
  return item.sentTime ? `实际 ${shortTime(item.sentTime)}` : '今日尚未产出'
}

/** 时点图标 */
function iconOf(point?: string): string {
  return ICONS[point ?? ''] ?? ICONS.morning!
}

/** 时点中文 */
function pointLabel(point?: string): string {
  switch (point) {
    case 'MORNING':
      return '早盘'
    case 'NOON':
      return '午盘'
    case 'PRE_CLOSE':
      return '收盘前'
    case 'EVENING':
      return '晚间'
    default:
      return point ?? '—'
  }
}

/** 留痕状态中文 */
function statusLabel(status?: string): string {
  switch (status) {
    case 'SENT':
      return '已发送'
    case 'RESENT':
      return '已补发'
    case 'FAILED':
      return '失败'
    case 'PENDING':
      return '进行中'
    default:
      return status ?? '—'
  }
}

/** 时点状态徽标 */
function pillClass(item: TaskPointStatusVO): string {
  if (item.status === 'SENT' || item.status === 'RESENT') {
    return 'app-pill--ok'
  }
  if (item.status === 'FAILED' || item.overdue) {
    return 'app-pill--bad'
  }
  return ''
}

/** 留痕状态徽标 */
function recordPill(status?: string): string {
  if (status === 'SENT' || status === 'RESENT') {
    return 'app-pill--ok'
  }
  if (status === 'FAILED') {
    return 'app-pill--bad'
  }
  return ''
}

/** 时间截短到时分 */
function shortTime(value?: string | null): string {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(5, 16)
}

/** 留存时间兜底取文件修改时间 */
function mailArchivedAt(mail: MailArchiveDisplay): string {
  return mail.archivedAt ? shortTime(mail.archivedAt) : '—'
}

/** 今天的日期，按本地时区取 yyyy-MM-dd */
function today(): string {
  const now = new Date()
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/**
 * 生成相对日期偏移的 ISO 日期
 *
 * @param offsetDays 偏移天数，负数表示往前
 */
function isoOf(offsetDays: number): string {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  return date.toISOString().slice(0, 10)
}

/**
 * 加载总览与留痕，两路失败互不影响
 */
async function load(): Promise<void> {
  loading.value = true
  const [overviewResult, recordResult] = await Promise.allSettled([
    taskOverview({ tradeDate: queryDate.value }),
    taskRecords({
      startDate: isoOf(-13),
      endDate: queryDate.value,
    }),
  ])
  if (overviewResult.status === 'fulfilled') {
    overview.value = (overviewResult.value.data ?? {}) as TaskOverviewDisplay
  } else {
    overview.value = null
    ElMessage.error('调度总览读取失败，请确认已登录')
  }
  records.value = recordResult.status === 'fulfilled' ? (recordResult.value.data ?? []) : []
  loading.value = false
}

/** 切换日期 */
function onDateChange(value: string): void {
  if (!value) {
    return
  }
  queryDate.value = value
  void load()
}

/**
 * 手动触发某个时点
 *
 * @param item   时点
 * @param resend 是否携带未送达补发
 */
async function onTrigger(item: TaskPointStatusVO, resend: boolean): Promise<void> {
  if (!item.point) {
    return
  }
  busy.value = true
  try {
    const result = await trigger({
      point: item.point,
      tradeDate: queryDate.value,
      resend,
    })
    const outcome = result.data ?? {}
    if (outcome.success === false) {
      ElMessage.error(`触发失败：${outcome.failReason ?? '未知原因'}`)
    } else {
      ElMessage.success(outcome.detail ?? '触发完成')
    }
    await load()
  } catch {
    // request 层已弹出原因
  } finally {
    busy.value = false
  }
}

/** 立即补跑 */
async function onCatchUp(): Promise<void> {
  busy.value = true
  try {
    const result = await catchUp()
    ElMessage.success(`补跑完成，补发 ${result.data ?? 0} 个时点`)
    await load()
  } catch {
    // request 层已弹出原因
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.app-point {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.app-point__desc {
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--t2);
}

.app-point__row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-point__time {
  font-size: 12px;
  color: var(--t3);
}

.app-point__fail {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--dn);
}

.app-point__path {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--t3);
}

.app-point__ops {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.app-point__dir {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--t3);
}

.app-mails {
  margin-top: 6px;
}

.app-mail {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--hair);
  font-size: 12.5px;
}

.app-mail:last-child {
  border-bottom: none;
}

.app-mail--bad {
  background: linear-gradient(90deg, transparent, var(--dn-soft), transparent);
}

.app-mail__tag {
  flex: none;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11.5px;
  background: var(--group-bg);
  color: var(--t3);
}

.app-mail__tag--bad {
  background: var(--dn-soft);
  color: var(--dn);
}

.app-mail__tx {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.app-mail__tx span {
  font-size: 11.5px;
  color: var(--t3);
}

.app-mail__file {
  margin-left: auto;
  flex: none;
  max-width: 40%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11.5px;
  color: var(--t4);
}

.app-records {
  display: flex;
  flex-direction: column;
}

.app-record {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px solid var(--hair);
  font-size: 12.5px;
}

.app-record:last-child {
  border-bottom: none;
}

.app-record__date {
  flex: none;
  color: var(--t2);
}

.app-record__point {
  flex: none;
  width: 52px;
  color: var(--t2);
}

.app-record__time {
  flex: none;
  color: var(--t3);
}

.app-record__why {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--dn);
}
</style>
