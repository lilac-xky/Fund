<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <AppTopBar active="lesson">
      <template #status>
        {{ loading ? '检查中…' : `检查 ${advisory?.checkedCount ?? 0} 笔` }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimable-count="advisory?.alertCount ?? 0"
      :total-count="advisory?.checkedCount ?? 0"
      :market-mood="advisory?.alertCount ? `命中 ${advisory.alertCount} 条` : '未命中'"
      :next-note="advisory?.participateInAlert === false ? '已关闭随邮件发出' : ''"
    />

    <div class="app-body">
      <AppSideRail
        section-label="错误模式"
        :items="railItems"
        :active="activePattern"
        footer-label="重新检查"
        :footer-icon="ICONS.refresh"
        @select="activePattern = $event"
        @footer="load"
      />

      <div class="app-stage app-stage--no-aside">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                复盘 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">历史教训</b>
              </div>
              <div class="app-eyebrow">Lessons</div>
              <h1 class="app-h1">历史教训警示</h1>
              <p class="app-lede">
                把追高被套、债基横盘后盲目换仓、连续亏损时恐慌卖出这三类重复犯错，
                按你的操作历史逐笔核对。命中的会给出当时依据的数值，以及你记下的教训。
              </p>
            </div>

            <div v-if="loading" class="app-loading">正在检查历史变动…</div>

            <AppAlert v-else-if="errorText" kind="error">{{ errorText }}</AppAlert>

            <template v-else>
              <AppAlert v-if="advisory?.paramUsable === false" kind="warn">
                警示参数不合法，本次不做判定：{{ (advisory?.paramIssues ?? []).join('；') }}
              </AppAlert>

              <AppAlert v-if="advisory?.notice" kind="info">{{ advisory.notice }}</AppAlert>

              <AppAlert v-if="advisory?.participateInAlert === false" kind="info">
                教训警示已在参数页关闭「随邮件发出」，这里仍可查看，但收盘前邮件不会带上。
              </AppAlert>

              <AppGroup
                v-if="visibleAlerts.length"
                title="命中的教训"
                code="ALERT"
                :meta="`${visibleAlerts.length} 条`"
                :meta-ok="false"
              >
                <template #icon>
                  <span v-html="ICONS.alert" />
                </template>

                <div v-for="(item, index) in visibleAlerts" :key="index" class="app-lesson">
                  <div class="app-lesson__head">
                    <span class="app-lesson__tag" :class="tagClass(item)">{{
                      item.patternLabel
                    }}</span>
                    <b class="app-lesson__name"> {{ item.varietyCode }} {{ item.varietyName }} </b>
                    <span v-if="item.triggerDate" class="app-lesson__date">
                      {{ item.triggerDate }}
                    </span>
                  </div>
                  <ul v-if="item.remarks?.length" class="app-lesson__why">
                    <li v-for="(text, row) in item.remarks" :key="row">{{ text }}</li>
                  </ul>
                  <p class="app-lesson__text">{{ item.lesson }}</p>
                </div>
              </AppGroup>

              <AppGroup
                v-else
                title="未命中任何教训"
                code="OK"
                meta="本次检查无警示"
                :meta-ok="true"
              >
                <template #icon>
                  <span v-html="ICONS.check" />
                </template>
                <div class="app-listempty">
                  {{ emptyText }}
                </div>
              </AppGroup>

              <AppGroup title="判定阈值" code="PARAM" meta="参数页可改">
                <template #icon>
                  <span v-html="ICONS.sliders" />
                </template>
                <div class="app-kv">
                  <div class="app-kv__row">
                    <span class="app-kv__k">追高容忍幅度</span>
                    <span class="app-kv__v">{{ fmt(advisory?.chaseTolerance) }}%</span>
                  </div>
                  <div class="app-kv__row">
                    <span class="app-kv__k">连续亏损天数</span>
                    <span class="app-kv__v">{{ advisory?.panicLossDays ?? '—' }} 日</span>
                  </div>
                  <div class="app-kv__row">
                    <span class="app-kv__k">随邮件发出</span>
                    <span class="app-kv__v">
                      {{ advisory?.participateInAlert === false ? '已关闭' : '开启' }}
                    </span>
                  </div>
                </div>
                <p class="app-hint">
                  这三类模式各有多条件，任一条不满足就不提醒。阈值改在「策略 / 警示参数」里。
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
 * 历史教训警示页：按三类错误模式核对历史操作，命中时给出依据数值与教训原文
 */
import { computed, onMounted, ref } from 'vue'
import AppAlert from '@/ui/AppAlert.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppSideRail from '@/layout/AppSideRail.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import { list as lessonList } from '@/api/lessonController'

/** 内联图标，避免引图标库 */
const ICONS: Record<string, string> = {
  alert:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4.5 3 20h18z"/><path d="M12 10v4.2M12 17.2v.2"/></svg>',
  check:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="m5 13 4.5 4.5L19 7"/></svg>',
  sliders:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h11M19 7h1M4 17h5M13 17h7"/><circle cx="17" cy="7" r="2"/><circle cx="11" cy="17" r="2"/></svg>',
  refresh:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M20 11a8 8 0 1 0-2 5"/><path d="M20 5v6h-6"/></svg>',
}

/** 三类模式，与后端 LessonPattern 一致 */
const PATTERNS = [
  { key: 'ALL', label: '全部' },
  { key: 'CHASE_HIGH', label: '追高被套' },
  { key: 'BLIND_SWITCH', label: '盲目换仓' },
  { key: 'PANIC_SELL', label: '恐慌卖出' },
] as const

/** 侧栏项 */
const railItems = PATTERNS.map((item) => ({ key: item.key, label: item.label, icon: ICONS.alert! }))

const loading = ref(true)
const errorText = ref('')
const advisory = ref<API.LessonAdvisoryVO | null>(null)
const activePattern = ref('ALL')

/** 按模式过滤后的命中项 */
const visibleAlerts = computed(() => {
  const items = advisory.value?.items ?? []
  if (activePattern.value === 'ALL') {
    return items
  }
  return items.filter((item) => item.pattern === activePattern.value)
})

/** 无命中时的说明，区分「没查」与「查了没命中」 */
const emptyText = computed(() => {
  if (activePattern.value !== 'ALL' && (advisory.value?.items ?? []).length > 0) {
    return '该模式本次没有命中，切回「全部」可以看其他模式。'
  }
  if ((advisory.value?.checkedCount ?? 0) === 0) {
    return advisory.value?.notice ?? '尚无历史买卖记录，先到「持仓」页录入后才会开始核对。'
  }
  return '本次检查的历史操作未命中三类错误模式。'
})

/**
 * 取教训警示
 *
 * @returns 无
 */
async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const result = await lessonList({})
    advisory.value = result.data ?? null
  } catch {
    errorText.value = '教训警示读取失败，请确认已登录'
    advisory.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 三种模式的标签配色，追高与恐慌用警示色，盲目换仓用强调色
 *
 * @param item 警示
 * @returns 类名
 */
function tagClass(item: API.LessonAlertVO): string {
  if (item.pattern === 'CHASE_HIGH' || item.pattern === 'PANIC_SELL') {
    return 'app-lesson__tag--warn'
  }
  return 'app-lesson__tag--info'
}

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
  return String(Number(value))
}

onMounted(load)
</script>

<style scoped>
/* 骨架类（app-page / app-body / app-stage / app-head 等）来自全局 styles/layout.css */
.app-kv {
  padding: 2px 0;
}

.app-lesson {
  padding: 12px 0;
  border-bottom: 1px solid var(--hair);
}

.app-lesson:last-child {
  border-bottom: none;
}

.app-lesson__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.app-lesson__tag {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11.5px;
}

.app-lesson__tag--warn {
  background: var(--dn-soft);
  color: var(--warn);
}

.app-lesson__tag--info {
  background: var(--acc-soft);
  color: var(--acc);
}

.app-lesson__name {
  font-size: 13px;
  color: var(--t1);
}

.app-lesson__date {
  font-size: 12px;
  color: var(--t3);
}

.app-lesson__why {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--t2);
}

.app-lesson__text {
  margin: 8px 0 0;
  padding: 8px 10px;
  border-left: 2px solid var(--acc-line);
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--t2);
  background: var(--group-bg);
}

.app-kv__row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid var(--hair);
  font-size: 12.5px;
}

.app-kv__row:last-child {
  border-bottom: none;
}

.app-kv__k {
  color: var(--t3);
}

.app-kv__v {
  color: var(--t1);
}
</style>
