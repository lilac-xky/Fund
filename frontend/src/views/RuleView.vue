<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <AppTopBar active="rule">
      <template #status>
        {{ loading ? '读取中…' : `${rules.length} 条规则` }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimable-count="enabledCount"
      :total-count="rules.length"
      :market-mood="masterOn ? '提醒开启' : '提醒已关闭'"
      :next-note="dirty ? '有未保存的改动' : ''"
    />

    <div class="app-body">
      <div class="app-stage app-stage--no-aside">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                策略 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">警示规则</b>
              </div>
              <div class="app-eyebrow">Rules</div>
              <h1 class="app-h1">警示规则</h1>
              <p class="app-lede">
                三类历史错误模式各有多条件，任一条不满足就不提醒。改动保存后立刻影响下一次收盘前邮件。
                想先看看会命中多少，用下面的试算。
              </p>
            </div>

            <div v-if="loading" class="app-loading">正在读取规则…</div>

            <AppAlert v-else-if="errorText" kind="error">{{ errorText }}</AppAlert>

            <template v-else>
              <AppGroup
                title="总开关"
                code="MASTER"
                :meta="masterOn ? '开启' : '关闭'"
                :meta-ok="masterOn"
              >
                <template #icon>
                  <span v-html="ICONS.power" />
                </template>
                <AppField
                  label="随收盘前邮件发出"
                  description="关闭后规则仍可在本页查看与试算，但邮件里不带教训警示"
                >
                  <AppSwitch :model-value="masterOn" @update:model-value="onMasterChange" />
                </AppField>
              </AppGroup>

              <AppGroup
                v-for="rule in rules"
                :key="rule.key"
                :title="rule.label"
                :code="rule.key"
                :meta="rule.enabled ? '生效中' : '已停用'"
                :meta-ok="rule.enabled"
              >
                <template #icon>
                  <span v-html="ICONS.rule" />
                </template>

                <p class="app-rule__desc">{{ rule.description }}</p>

                <AppField
                  v-for="field in rule.fields"
                  :key="field.key"
                  :label="field.label ?? ''"
                  :description="field.description"
                  :unit="field.unit"
                >
                  <AppInput
                    :model-value="draft[field.key ?? ''] ?? ''"
                    :numeric="field.type !== 'TEXT'"
                    @update:model-value="(value) => setValue(field.key ?? '', value)"
                  />
                </AppField>

                <p class="app-rule__note">{{ rule.note }}</p>
              </AppGroup>

              <div class="app-actions">
                <AppButton :disabled="saving || !dirty" @click="save">
                  {{ saving ? '保存中…' : dirty ? '保存改动' : '无改动' }}
                </AppButton>
                <AppButton size="sm" :disabled="saving" @click="load">放弃改动并重新读取</AppButton>
              </div>

              <AppGroup title="试算" code="TRIAL" meta="用历史数据回看">
                <template #icon>
                  <span v-html="ICONS.flask" />
                </template>
                <p class="app-rule__desc">
                  用上面填的阈值在过去的买卖记录上跑一遍，看看会命中多少条。
                  结果会回显实际使用的参数，便于对照——改动参数后再试算，两次结果才可比。
                </p>
                <AppAlert kind="info">
                  试算接口（`POST /lesson/trial`）尚未生成到 `src/api/`，
                  重新生成后此处会出现「开始试算」按钮与命中明细。
                </AppAlert>
              </AppGroup>

              <p class="app-hint">阈值改动在保存后立刻生效，不需要重启。试算不落库，可以随便试。</p>
            </template>
          </div>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 警示规则页：列出三类规则的条件，可改阈值、可总开关，并留出试算位置
 */
import { computed, onMounted, reactive, ref } from 'vue'
import AppAlert from '@/ui/AppAlert.vue'
import AppButton from '@/ui/AppButton.vue'
import AppField from '@/ui/AppField.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppInput from '@/ui/AppInput.vue'
import AppSwitch from '@/ui/AppSwitch.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import { overview as settingOverview, save as settingSave } from '@/api/settingController'
import { ElMessage } from '@/ui/message'

/** 内联图标 */
const ICONS: Record<string, string> = {
  power:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v9"/><path d="M6.5 7a8 8 0 1 0 11 0"/></svg>',
  rule: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 6h16M4 12h10M4 18h13"/><circle cx="18" cy="12" r="2"/></svg>',
  flask:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M9 3h6M10 3v6.5L4.8 18A2 2 0 0 0 6.5 21h11a2 2 0 0 0 1.7-3L14 9.5V3"/><path d="M7.5 15h9"/></svg>',
}

/** ALERT 分组里属于教训警示的字段名 */
const LESSON_KEYS = [
  'chaseTolerance',
  'chaseWindow',
  'panicLossDays',
  'panicLossRate',
  'familiarDays',
  'lessonParticipateInAlert',
] as const

/** 三类规则的定义：哪几个字段属于它，以及判定口径说明 */
const RULE_DEFS = [
  {
    key: 'CHASE_HIGH',
    label: '追高被套',
    keys: ['chaseTolerance', 'chaseWindow'],
    description: '买入价高于此前一段时间最高收盘价超过容忍幅度时提醒。',
    note: '判定只用建仓日之前的日线，不会拿买入之后的最高价来比——那会变成「每次都追高」。场外基金没有日线，这条判不出来。',
  },
  {
    key: 'BLIND_SWITCH',
    label: '盲目换仓',
    keys: ['familiarDays'],
    description: '同一天从债券换出、且该债券已达横盘阈值、且换入品种登记不足设定天数时提醒。',
    note: '「熟悉程度」用品种在本工具里的登记天数近似。你早就熟悉但从没登记过的基金会被判成不熟悉。',
  },
  {
    key: 'PANIC_SELL',
    label: '恐慌卖出',
    keys: ['panicLossDays', 'panicLossRate'],
    description: '卖出前连续多日低于成本价，且累计亏损达到设定深度时提醒。',
    note: '只跌几天但亏得浅、或单日暴跌，都不算——要求「连续」与「深度」两个条件同时成立。',
  },
] as const

const loading = ref(true)
const saving = ref(false)
const errorText = ref('')
const masterOn = ref(true)
const draft = reactive<Record<string, string>>({})
const baseline = reactive<Record<string, string>>({})
const fields = ref<API.SettingItemVO[]>([])

/** 把字段按规则分组 */
const rules = computed(() =>
  RULE_DEFS.map((def) => ({
    key: def.key,
    label: def.label,
    description: def.description,
    note: def.note,
    enabled: masterOn.value,
    fields: fields.value.filter((item) => def.keys.includes((item.key ?? '') as never)),
  })).filter((rule) => rule.fields.length > 0),
)

/** 生效中的规则数 */
const enabledCount = computed(() => (masterOn.value ? rules.value.length : 0))

/** 是否有未保存改动 */
const dirty = computed(() => Object.keys(draft).some((key) => draft[key] !== baseline[key]))

/**
 * 读取 ALERT 分组的教训相关字段
 *
 * @returns 无
 */
async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const result = await settingOverview()
    const alert = (result.data?.groups ?? []).find((group) => group.group === 'ALERT')
    const items = (alert?.items ?? []).filter((item) =>
      LESSON_KEYS.includes((item.key ?? '') as never),
    )
    fields.value = items
    Object.keys(draft).forEach((key) => delete draft[key])
    Object.keys(baseline).forEach((key) => delete baseline[key])
    items.forEach((item) => {
      const key = item.key ?? ''
      if (item.type === 'BOOLEAN') {
        masterOn.value = item.value !== 'false'
        return
      }
      draft[key] = item.value ?? ''
      baseline[key] = item.value ?? ''
    })
  } catch {
    errorText.value = '规则读取失败，请确认已登录'
  } finally {
    loading.value = false
  }
}

/**
 * 写一个字段的值 *
 * @param key   字段名
 * @param value 值
 * @returns 无
 */
function setValue(key: string, value: string): void {
  if (key) {
    draft[key] = value
  }
}

/**
 * 总开关变化，直接进 draft 等保存
 *
 * @param value 是否开启
 * @returns 无
 */
function onMasterChange(value: boolean): void {
  draft['lessonParticipateInAlert'] = value ? 'true' : 'false'
  masterOn.value = value
}

/**
 * 保存改动到 ALERT 分组
 *
 * @returns 无
 */
async function save(): Promise<void> {
  const values: Record<string, string> = {}
  Object.keys(draft).forEach((key) => {
    if (draft[key] !== baseline[key] || key === 'lessonParticipateInAlert') {
      values[key] = draft[key] ?? ''
    }
  })
  if (Object.keys(values).length === 0) {
    return
  }
  saving.value = true
  try {
    const result = await settingSave({
      groups: [{ group: 'ALERT', values: values as unknown as API.MapString }],
    })
    const data = result.data ?? {}
    if (data.ok) {
      ElMessage.success(data.message ?? '已保存')
      await load()
    } else {
      const first = (data.errors ?? [])[0]
      errorText.value = first
        ? `${first.label ?? first.field}：${first.message}`
        : (data.message ?? '保存失败')
    }
  } catch {
    errorText.value = '保存失败，请查看接口返回的原因'
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  draft['lessonParticipateInAlert'] = 'true'
  baseline['lessonParticipateInAlert'] = 'true'
  void load()
})
</script>

<style scoped>
/* 骨架类来自全局 styles/layout.css */
.app-rule__desc {
  margin: 0 0 10px;
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--t2);
}

.app-rule__note {
  margin: 10px 0 0;
  padding: 8px 10px;
  border-left: 2px solid var(--acc-line);
  background: var(--group-bg);
  font-size: 12px;
  line-height: 1.75;
  color: var(--t3);
}

.app-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 4px 0 16px;
}
</style>
