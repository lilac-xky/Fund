<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <AppTopBar active="strategy">
      <template #status>
        {{ mail.ready ? '邮件通道就绪' : '邮件通道未就绪' }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimable-count="checks.filter((c) => c.pass).length"
      :total-count="checks.length"
      :market-mood="mail.ready ? '通道就绪' : '待配置'"
      :next-note="lastSavedAt ? `最后保存 ${lastSavedAt}` : ''"
    />

    <div class="app-body">
      <AppSideRail
        section-label="参数分组"
        :items="railItems"
        :active="activeGroup"
        footer-label="全部恢复默认"
        :footer-icon="GROUP_ICONS.ALERT"
        @select="scrollTo"
        @footer="resetOne('INDICATOR')"
      />

      <div class="app-stage">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                设置 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">参数</b>
              </div>
              <div class="app-eyebrow">Settings</div>
              <h1 class="app-h1">参数设置</h1>
              <p class="app-lede">
                所有策略参数设定后长期有效。改动在保存成功后的下一个定时时点生效，当日已发出的邮件不会重发。
              </p>
            </div>

            <div v-if="loading" class="app-loading">正在读取参数…</div>

            <div v-else-if="globalError" class="app-alert">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
              >
                <circle cx="12" cy="12" r="9.2" />
                <path d="M12 7.5v5.2M12 16.3v.2" />
              </svg>
              <span>{{ globalError }}</span>
            </div>

            <AppGroup
              v-if="activeGroupMeta"
              :key="activeGroupMeta.group"
              :title="activeGroupMeta.label ?? ''"
              :code="activeGroupMeta.group ?? ''"
              :meta="activeGroupMeta.group === 'INDICATOR' ? '已内置默认值，不改也能跑' : ''"
              :meta-ok="activeGroupMeta.group === 'INDICATOR'"
            >
              <template #icon>
                <span v-html="GROUP_ICONS[activeGroupMeta.group ?? ''] ?? ''" />
              </template>

              <AppField
                v-for="item in activeGroupMeta.items ?? []"
                :key="item.key"
                :label="item.label ?? ''"
                :description="item.description"
                :error="errorOf(activeGroupMeta.group ?? '', item.key ?? '')"
              >
                <AppSwitch
                  v-if="item.type === 'BOOLEAN'"
                  :model-value="valueOf(activeGroupMeta.group ?? '', item.key ?? '') === 'true'"
                  @update:model-value="
                    (value) =>
                      setValue(
                        activeGroupMeta!.group ?? '',
                        item.key ?? '',
                        value ? 'true' : 'false',
                      )
                  "
                />
                <template v-else>
                  <AppInput
                    :model-value="valueOf(activeGroupMeta.group ?? '', item.key ?? '')"
                    :numeric="item.type !== 'TEXT'"
                    :invalid="Boolean(errorOf(activeGroupMeta.group ?? '', item.key ?? ''))"
                    :inputmode="item.type === 'TEXT' ? 'text' : 'decimal'"
                    @update:model-value="
                      (value) => setValue(activeGroupMeta!.group ?? '', item.key ?? '', value)
                    "
                  />
                  <span v-if="item.unit" class="app-unit">{{ item.unit }}</span>
                </template>
              </AppField>
            </AppGroup>

            <AppGroup
              v-if="activeGroup === 'MAIL'"
              title="邮件与 SMTP"
              code="MAIL"
              meta="授权码只写入，任何接口都不回显"
            >
              <template #icon>
                <span v-html="GROUP_ICONS.MAIL" />
              </template>

              <AppField
                label="收件地址"
                description="多个地址用逗号分隔"
                :error="errorOf('MAIL', 'recipients')"
              >
                <AppInput
                  :model-value="draft['MAIL.recipients'] ?? ''"
                  :numeric="false"
                  :invalid="Boolean(errorOf('MAIL', 'recipients'))"
                  inputmode="email"
                  @update:model-value="(value) => (draft['MAIL.recipients'] = value)"
                />
              </AppField>

              <AppField
                label="SMTP 服务器与端口"
                description="465 走 SSL，587 走 STARTTLS"
                :error="errorOf('MAIL', 'port') ?? errorOf('MAIL', 'host')"
              >
                <AppInput
                  :model-value="draft['MAIL.host'] ?? ''"
                  :numeric="false"
                  :invalid="Boolean(errorOf('MAIL', 'host'))"
                  placeholder="smtp.qq.com"
                  @update:model-value="(value) => (draft['MAIL.host'] = value)"
                />
                <AppInput
                  :model-value="draft['MAIL.port'] ?? ''"
                  class="app-port"
                  :invalid="Boolean(errorOf('MAIL', 'port'))"
                  inputmode="numeric"
                  @update:model-value="(value) => (draft['MAIL.port'] = value)"
                />
              </AppField>

              <AppField
                label="账号"
                description="发件邮箱账号，缺省时与收件地址相同"
                :error="errorOf('MAIL', 'username')"
              >
                <AppInput
                  :model-value="draft['MAIL.username'] ?? ''"
                  :numeric="false"
                  inputmode="email"
                  @update:model-value="(value) => (draft['MAIL.username'] = value)"
                />
              </AppField>

              <AppField
                label="授权码"
                description="写入密钥文件，接口只回「已配置 / 未配置」"
                :hint="mail.secretsFile ? '密钥文件：' + mail.secretsFile : undefined"
              >
                <div class="app-secret">
                  <span class="app-secret__mask">••••••••••••</span>
                  <span
                    class="app-secret__chip"
                    :class="{ 'app-secret__chip--off': !mail.passwordConfigured }"
                  >
                    {{ mail.passwordConfigured ? '已配置' : '未配置' }}
                  </span>
                  <button type="button" class="app-secret__btn" @click="focusPassword">更换</button>
                </div>
              </AppField>

              <AppField label="新授权码" description="留空表示不修改；写入后任何接口都不回显">
                <AppInput
                  v-model="newPassword"
                  type="password"
                  :numeric="false"
                  placeholder="输入新的授权码"
                  data-password-input
                />
                <label class="app-clear">
                  <input v-model="clearPassword" type="checkbox" />
                  清除已保存
                </label>
              </AppField>
            </AppGroup>

            <div class="app-notice">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="12" cy="12" r="9.2" />
                <path d="M12 11v5.4M12 7.6v.2" />
              </svg>
              <div class="app-notice__tx">
                <b>生效范围</b>
                　红利与网格参数进入盘中建议与收盘前邮件；指标参数影响午盘页的指标状态与提醒；邮件参数在下一个收件时点生效。改动在保存成功后开始使用，不回填当日已发出的邮件。
                <span v-if="effectiveNote" class="app-notice__note">{{ effectiveNote }}</span>
              </div>
            </div>
          </div>
        </main>

        <aside class="app-aside">
          <AppAsideBlock title="本次改动">
            <template #count>
              <span v-if="changes.length" class="app-count">{{ changes.length }}</span>
            </template>
            <div class="app-diff">
              <div v-if="changes.length === 0" class="app-diff__empty">还没有改动</div>
              <div v-for="(change, index) in changes" :key="index" class="app-diff__row">
                <div class="app-diff__head">
                  <span class="app-diff__label">{{ change.label }}</span>
                  <span v-if="change.invalid" class="app-diff__bad">校验失败</span>
                </div>
                <div class="app-diff__vals">
                  <span class="app-diff__old">{{ change.from }}</span>
                  <span class="app-diff__arw">→</span>
                  <span class="app-diff__new" :class="{ 'app-diff__new--bad': change.invalid }">
                    {{ change.to }}
                  </span>
                </div>
              </div>
            </div>
          </AppAsideBlock>

          <AppAsideBlock title="校验清单">
            <template #count>
              <span class="app-count">{{ checks.length }}</span>
            </template>
            <div class="app-checks">
              <div
                v-for="(check, index) in checks"
                :key="index"
                class="app-check"
                :class="{ 'app-check--fail': !check.pass }"
              >
                <svg
                  v-if="check.pass"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="m5 12.5 4.5 4.5L19 7" />
                </svg>
                <svg
                  v-else
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                >
                  <circle cx="12" cy="12" r="9.2" />
                  <path d="M12 7.5v5.2M12 16.3v.2" />
                </svg>
                <span>{{ check.label }}</span>
              </div>
            </div>
          </AppAsideBlock>

          <AppAsideBlock title="邮件通道">
            <div class="app-mailbox" :class="{ 'app-mailbox--off': !mail.ready }">
              <b>{{ mail.ready ? '通道就绪' : '通道未就绪' }}</b>
              {{ mail.note || '尚未配置 SMTP 参数' }}
            </div>
          </AppAsideBlock>
        </aside>
      </div>
    </div>

    <AppSaveBar
      :error-count="errorCount"
      :first-error="firstError"
      :dirty-count="dirtyCount"
      :last-saved-at="lastSavedAt"
      :saving="saving"
      @save="saveAll"
      @discard="discard"
    />

    <MessageHost />
  </div>
</template>

<script setup lang="ts">
/**
 * 参数设置页：四个分组双列铺开、行内校验、右栏显示改动与校验清单
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AppField from '@/ui/AppField.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppInput from '@/ui/AppInput.vue'
import AppSwitch from '@/ui/AppSwitch.vue'
import AppSaveBar from '@/layout/AppSaveBar.vue'
import AppAsideBlock from '@/layout/AppAsideBlock.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppSideRail from '@/layout/AppSideRail.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import MessageHost from '@/ui/MessageHost.vue'
import {
  mailSetting as mailSettingApi,
  overview as overviewApi,
  reset as resetApi,
  save as saveApi,
  saveMailSetting as saveMailApi,
} from '@/api/settingController'

/** 待保存的分组，values 是「参数键 → 参数值」的键值表 */
interface SettingGroupPayload {
  group: string
  values: Record<string, string>
}

/** 分组图标，用内联 SVG 以免引入图标库 */
const GROUP_ICONS: Record<string, string> = {
  DIVIDEND:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v18M5 8l7-5 7 5M5 16l7 5 7-5"/></svg>',
  GRID: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M3 12h18M3 18h18M8 3v18M16 3v18"/></svg>',
  INDICATOR:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 17l5-6 4 3 4-7 5 4"/></svg>',
  ALERT:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4.5 4 9v6l8 4.5 8-4.5V9z"/><path d="M12 4.5V20"/></svg>',
  MAIL: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="m4 7.5 8 5.5 8-5.5"/></svg>',
}

/** 侧栏锚点，数量按分组实际字段数给 */
const railItems = [
  { key: 'DIVIDEND', label: '红利策略', icon: GROUP_ICONS.DIVIDEND!, count: 4 },
  { key: 'GRID', label: '网格策略', icon: GROUP_ICONS.GRID!, count: 4 },
  { key: 'INDICATOR', label: '技术指标', icon: GROUP_ICONS.INDICATOR!, count: 10 },
  { key: 'ALERT', label: '警示参数', icon: GROUP_ICONS.ALERT!, count: 3 },
  { key: 'MAIL', label: '邮件与 SMTP', icon: GROUP_ICONS.MAIL!, count: 5 },
]

/** 当前激活的侧栏项 */
const activeGroup = ref('DIVIDEND')

/** 当前显示的分组；MAIL 不在 groups 里，单独由邮件区块渲染 */
const activeGroupMeta = computed(() =>
  groups.value.find((item) => item.group === activeGroup.value),
)

/** 页面加载中 */
const loading = ref(true)

/** 保存中 */
const saving = ref(false)

/** 分组数据，来自生成的 SettingGroupVO */
const groups = ref<API.SettingGroupVO[]>([])

/** 编辑中的值，键为「分组.字段」 */
const draft = reactive<Record<string, string>>({})

/** 服务端的原始值，用于算脏值与改动对照 */
const baseline = reactive<Record<string, string>>({})

/** 字段级错误，键为「分组.字段」 */
const errors = reactive<Record<string, string>>({})

/** 顶层错误，接口整体失败时展示 */
const globalError = ref('')

/** 邮件设置 */
const mail = reactive<API.MailSettingVO>({})

/** 新授权码，只写不回显，保存后立即清空 */
const newPassword = ref('')

/** 是否要求清除授权码 */
const clearPassword = ref(false)

/** 最后保存时间 */
const lastSavedAt = ref('')

/** 生效说明 */
const effectiveNote = ref('')

/** 授权码输入框引用，点「更换」时聚焦 */
const passwordInput = ref<InstanceType<typeof AppInput> | null>(null)

/** 邮件普通字段的名称集合 */
const MAIL_FIELDS = ['recipients', 'host', 'port', 'username', 'from'] as const

/** 参数分组名称集合，邮件走独立接口 */
const PARAM_GROUPS = ['DIVIDEND', 'GRID', 'INDICATOR', 'ALERT'] as const

/** 键拼装 */
function keyOf(group: string, field: string): string {
  return `${group}.${field}`
}

/** 按分组取字段列表 */
function itemsOf(group: string): API.SettingItemVO[] {
  return groups.value.find((item) => item.group === group)?.items ?? []
}

/** 脏值数量 */
const dirtyCount = computed(() => {
  let count = 0
  Object.keys(draft).forEach((key) => {
    if (draft[key] !== baseline[key]) {
      count++
    }
  })
  if (newPassword.value.trim() || clearPassword.value) {
    count++
  }
  return count
})

/** 错误条数 */
const errorCount = computed(() => Object.keys(errors).length)

/** 第一条错误文案 */
const firstError = computed(() => Object.values(errors)[0] ?? '')

/** 改动清单，用于右栏对照 */
const changes = computed(() => {
  const list: Array<{ label: string; from: string; to: string; invalid: boolean }> = []
  groups.value.forEach((group) => {
    ;(group.items ?? []).forEach((item) => {
      const key = keyOf(group.group ?? '', item.key ?? '')
      if (draft[key] !== undefined && draft[key] !== baseline[key]) {
        list.push({
          label: item.label ?? '',
          from: baseline[key] || '（未设置）',
          to: draft[key] || '（空）',
          invalid: Boolean(errors[key]),
        })
      }
    })
  })
  return list
})

/** 校验清单，页面右侧逐项显示 */
const checks = computed(() => {
  const value = (field: string) => draft[keyOf('DIVIDEND', field)]
  const list: Array<{ label: string; pass: boolean }> = []
  const base = Number(value('baseAmount'))
  const floor = Number(value('floorAmount'))
  list.push({ label: '基准仓位为正数', pass: Number.isFinite(base) && base > 0 })
  list.push({
    label: '保底分红 ≤ 基准仓位',
    pass: Number.isFinite(floor) && Number.isFinite(base) && floor <= base,
  })
  const addYield = Number(value('addYield'))
  const reduceYield = Number(value('reduceYield'))
  list.push({
    label: '减仓阈值 < 加仓阈值',
    pass: Number.isFinite(addYield) && Number.isFinite(reduceYield) && reduceYield < addYield,
  })
  list.push({ label: 'SMTP 授权码已配置', pass: Boolean(mail.passwordConfigured) })
  return list
})

/** 把草稿与基线一起写入，未自定义的字段基线留空以便识别脏值 */
function seed(next: API.SettingGroupVO[]): void {
  groups.value = next
  Object.keys(draft).forEach((key) => delete draft[key])
  Object.keys(baseline).forEach((key) => delete baseline[key])
  next.forEach((group) => {
    ;(group.items ?? []).forEach((item) => {
      const key = keyOf(group.group ?? '', item.key ?? '')
      draft[key] = item.value ?? ''
      baseline[key] = item.customized ? (item.value ?? '') : ''
    })
  })
}

/** 拉取页面数据 */
async function load(): Promise<void> {
  loading.value = true
  globalError.value = ''
  try {
    const overviewResult = await overviewApi()
    const overview = overviewResult.data ?? {}
    seed(overview.groups ?? [])
    effectiveNote.value = overview.effectiveNote ?? ''
    const mailResult = await mailSettingApi()
    const mailSetting = mailResult.data ?? {}
    Object.assign(mail, mailSetting)
    MAIL_FIELDS.forEach((field) => {
      const key = `MAIL.${field}`
      draft[key] = mailSetting[field] ?? ''
      baseline[key] = mailSetting[field] ?? ''
    })
  } catch {
    // 出错时保留上次成功结果，不白屏
    globalError.value = '读取参数失败，页面显示的是上次成功的结果'
  } finally {
    loading.value = false
  }
}

/** 收集待保存的分组，只提交变过的字段 */
function collectPayload(): SettingGroupPayload[] {
  const payload: SettingGroupPayload[] = []
  PARAM_GROUPS.forEach((groupName) => {
    const values: Record<string, string> = {}
    itemsOf(groupName).forEach((item) => {
      const key = keyOf(groupName, item.key ?? '')
      if (draft[key] !== baseline[key]) {
        values[item.key ?? ''] = draft[key] ?? ''
      }
    })
    if (Object.keys(values).length > 0) {
      payload.push({ group: groupName, values })
    }
  })
  return payload
}

/** 邮件字段是否有改动 */
function mailChanged(): boolean {
  const fieldChanged = MAIL_FIELDS.some(
    (field) => (draft[`MAIL.${field}`] ?? '') !== (baseline[`MAIL.${field}`] ?? ''),
  )
  return fieldChanged || Boolean(newPassword.value.trim()) || clearPassword.value
}

/** 组装邮件保存入参 */
function buildMailPayload(): API.MailSettingSaveRequest {
  return {
    recipients: draft['MAIL.recipients'],
    host: draft['MAIL.host'],
    port: draft['MAIL.port'],
    username: draft['MAIL.username'],
    from: draft['MAIL.from'],
    ...(newPassword.value.trim() ? { password: newPassword.value.trim() } : {}),
    ...(clearPassword.value ? { clearPassword: true } : {}),
  }
}

/** 保存全部改动，参数与邮件任一失败即标记失败并保留错误 */
async function saveAll(): Promise<void> {
  Object.keys(errors).forEach((key) => delete errors[key])
  const payload = collectPayload()
  const mailDirty = mailChanged()
  if (payload.length === 0 && !mailDirty) {
    return
  }

  saving.value = true
  try {
    let failed = false
    if (payload.length > 0) {
      // 生成层把 Map<String,String> 映射成了 { key?: string }，真实语义是键值表，此处转一次
      const body = {
        groups: payload.map((item) => ({
          group: item.group,
          values: item.values as unknown as API.MapString,
        })),
      }
      const saveResult = await saveApi(body as API.SettingBatchSaveRequest)
      const result = saveResult.data ?? {}
      if (result.ok) {
        payload.forEach((group) => {
          Object.keys(group.values).forEach((field) => {
            const key = keyOf(group.group, field)
            baseline[key] = draft[key] ?? ''
          })
        })
      } else {
        failed = true
        collectErrors(result.errors ?? [])
      }
    }
    if (mailDirty) {
      const mailSaveResult = await saveMailApi(buildMailPayload())
      const mailResult = mailSaveResult.data ?? {}
      if (mailResult.ok) {
        const refreshedResult = await mailSettingApi()
        Object.assign(mail, refreshedResult.data ?? {})
        MAIL_FIELDS.forEach((field) => {
          const key = `MAIL.${field}`
          baseline[key] = draft[key] ?? ''
        })
        newPassword.value = ''
        clearPassword.value = false
      } else {
        failed = true
        collectErrors(mailResult.errors ?? [])
      }
    }
    if (!failed) {
      lastSavedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
      await load()
    }
  } finally {
    saving.value = false
  }
}

/** 把字段级错误摊平到 errors，无字段名的归到顶层 */
function collectErrors(list: API.SettingFieldError[]): void {
  list.forEach((item) => {
    if (item.field) {
      errors[keyOf(item.group ?? '', item.field)] = item.message ?? '参数不合法'
    } else {
      globalError.value = item.message ?? '参数不合法'
    }
  })
}

/** 放弃修改，回到基线 */
function discard(): void {
  Object.keys(draft).forEach((key) => {
    if (baseline[key] !== undefined) {
      draft[key] = baseline[key]
    }
  })
  newPassword.value = ''
  clearPassword.value = false
  Object.keys(errors).forEach((key) => delete errors[key])
}

/** 恢复单个分组默认值 */
async function resetOne(group: string): Promise<void> {
  const result = await resetApi({ group })
  if (result.data?.ok) {
    await load()
  }
}

/** 键盘快捷键，Cmd/Ctrl + S 触发保存 */
function onKeydown(event: KeyboardEvent): void {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    void saveAll()
  }
}

/** 切换分组，左栏点击即整页切换而不是页内滚动 */
function scrollTo(group: string): void {
  activeGroup.value = group
}

/** 输入框的错误提示 */
function errorOf(group: string, field: string): string | undefined {
  return errors[keyOf(group, field)]
}

/** 字段值读写 */
function valueOf(group: string, field: string): string {
  return draft[keyOf(group, field)] ?? ''
}

/** 写入字段值 */
function setValue(group: string, field: string, value: string): void {
  draft[keyOf(group, field)] = value
}

/** 点「更换」时聚焦授权码输入框 */
function focusPassword(): void {
  clearPassword.value = false
  const element = document.querySelector<HTMLInputElement>('input[data-password-input]')
  element?.focus()
}

watch(
  () => draft['DIVIDEND.baseAmount'],
  () => {
    delete errors[keyOf('DIVIDEND', 'floorAmount')]
  },
)

onMounted(() => {
  void load()
  window.addEventListener('keydown', onKeydown)
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
  padding: 26px 26px 118px;
}

.app-center__in {
  max-width: 880px;
  margin: 0 auto;
}

.app-aside {
  align-self: stretch;
  min-height: calc(100vh - var(--topbar) - var(--pulse));
  padding: 26px 18px 118px;
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

  .app-center__in {
    max-width: 720px;
  }
}

@media (max-width: 860px) {
  .app-body {
    padding-top: var(--topbar);
    padding-left: 0;
  }

  .app-center {
    padding: 20px 16px 110px;
  }
}

.app-unit {
  flex: none;
  font-size: 11px;
  color: var(--t4);
}

/* ── 页面头部 ── */
.app-head {
  margin-bottom: 24px;
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
  max-width: 600px;
  margin-top: 9px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--t3);
}

.app-loading {
  padding: 64px 0;
  text-align: center;
  font-size: 12.5px;
  color: var(--t4);
}

.app-alert {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid var(--acc-line);
  border-radius: 10px;
  background: var(--acc-soft);
  color: var(--acc);
  font-size: 12px;
}

.app-alert svg {
  flex: none;
  width: 14px;
  height: 14px;
  margin-top: 1px;
}

/* ── 生效说明 ── */
.app-notice {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 12px;
  padding: 15px 16px;
  border-radius: 11px;
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
}

.app-notice svg {
  flex: none;
  width: 14px;
  height: 14px;
  margin-top: 1px;
  color: var(--acc);
}

.app-notice__tx {
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--t2);
}

.app-notice__tx b {
  font-weight: 650;
  color: var(--t1);
}

.app-notice__note {
  display: block;
  margin-top: 4px;
  color: var(--t4);
}

/* ── 右栏 ── */
.app-count {
  margin-left: auto;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--s3);
  color: var(--t4);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0;
}

.app-diff {
  overflow: hidden;
  border: 1px solid var(--hair);
  border-radius: 11px;
  background: var(--s1);
  box-shadow: var(--group-hi), var(--sh-sm);
}

.app-diff__empty {
  padding: 16px 12px;
  text-align: center;
  font-size: 11.5px;
  color: var(--t4);
}

.app-diff__row {
  padding: 10px 12px;
  border-bottom: 1px solid var(--hair);
  font-size: 11.5px;
}

.app-diff__row:last-child {
  border-bottom: 0;
}

.app-diff__head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 5px;
  color: var(--t3);
}

.app-diff__label {
  font-weight: 500;
  color: var(--t2);
}

.app-diff__bad {
  padding: 0 5px;
  border-radius: 3px;
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
  color: var(--acc);
  font-size: 10px;
  font-weight: 650;
}

.app-diff__vals {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  font-variant-numeric: tabular-nums;
}

.app-diff__old {
  color: var(--diff-del);
  text-decoration: line-through;
  opacity: 0.8;
}

.app-diff__arw {
  color: var(--t4);
  font-size: 10px;
}

.app-diff__new {
  font-weight: 600;
  color: var(--diff-add);
}

.app-diff__new--bad {
  color: var(--acc);
}

.app-checks {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.app-check {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 6px 8px;
  border-radius: 6px;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--t3);
}

.app-check svg {
  flex: none;
  width: 13px;
  height: 13px;
  margin-top: 1px;
  stroke: var(--dn);
  fill: none;
}

.app-check--fail {
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
  color: var(--acc);
}

.app-check--fail svg {
  stroke: var(--acc);
}

.app-mailbox {
  padding: 12px 14px;
  border-radius: 11px;
  background: var(--dn-soft);
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--t2);
}

.app-mailbox--off {
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
}

.app-mailbox b {
  display: block;
  margin-bottom: 4px;
  font-weight: 650;
  color: var(--t1);
}

.app-port {
  max-width: 92px;
}

.app-secret {
  display: flex;
  align-items: center;
  flex: 1;
  gap: 8px;
  height: 36px;
  padding: 0 6px 0 12px;
  border: 1px solid var(--hair-2);
  border-radius: 8px;
  background: var(--s2);
}

.app-secret__mask {
  flex: 1;
  font-size: 12.5px;
  letter-spacing: 0.26em;
  color: var(--t4);
}

.app-secret__chip {
  flex: none;
  padding: 1.5px 7px;
  border-radius: 4px;
  background: var(--dn-soft);
  box-shadow: inset 0 0 0 1px var(--dn-soft);
  color: var(--dn);
  font-size: 10px;
  font-weight: 700;
}

.app-secret__chip--off {
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
  color: var(--acc);
}

.app-secret__btn {
  flex: none;
  height: 27px;
  padding: 0 10px;
  border: 1px solid var(--hair-2);
  border-radius: 6px;
  background: var(--btn-bg);
  box-shadow: var(--sh-sm);
  color: var(--t2);
  font: inherit;
  font-size: 11.5px;
  cursor: pointer;
  transition: 0.15s;
}

.app-secret__btn:hover {
  border-color: var(--hair-3);
  background: var(--btn-hover-bg);
  color: var(--t1);
}

.app-clear {
  display: flex;
  align-items: center;
  flex: none;
  gap: 6px;
  font-size: 11px;
  color: var(--t4);
  cursor: pointer;
  white-space: nowrap;
}

.app-clear input {
  accent-color: var(--acc);
}
</style>
