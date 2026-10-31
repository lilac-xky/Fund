<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <AppTopBar active="category">
      <template #status>
        {{ loading ? '读取中…' : `待确认 ${rows.length} 只` }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimable-count="rows.length - selectedKeys.length"
      :total-count="rows.length"
      :market-mood="rows.length ? '需人工指定' : '无待确认'"
      :next-note="selectedKeys.length ? `已选 ${selectedKeys.length} 只` : ''"
    />

    <div class="app-body">
      <div class="app-stage app-stage--no-aside">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                持仓 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">类型待确认</b>
              </div>
              <div class="app-eyebrow">Category</div>
              <h1 class="app-h1">待确认类型</h1>
              <p class="app-lede">
                上游只给三类基金类型，「黄金」与「红利」要靠名称关键词推断。推断不出来时这里会列出来，
                指定后预估与红利、债券、网格策略立即按新类型生效。
              </p>
              <div class="app-daybar">
                <label class="app-check">
                  <input v-model="holdingOnly" type="checkbox" @change="load" />
                  <span>只看持有中的</span>
                </label>
                <AppButton size="sm" :disabled="loading" @click="load">重新读取</AppButton>
              </div>
            </div>

            <div v-if="loading" class="app-loading">正在读取待确认清单…</div>

            <AppAlert v-else-if="errorText" kind="error">{{ errorText }}</AppAlert>

            <template v-else>
              <AppAlert v-if="!rows.length" kind="ok"> 没有待确认类型的标的。 </AppAlert>

              <AppGroup
                v-else
                title="待指定"
                code="PENDING"
                :meta="`${rows.length} 只`"
                :meta-ok="false"
              >
                <template #icon>
                  <span v-html="ICONS.flag" />
                </template>

                <div class="app-pick">
                  <label class="app-check">
                    <input
                      type="checkbox"
                      :checked="allSelected"
                      @change="toggleAll(($event.target as HTMLInputElement).checked)"
                    />
                    <span>全选</span>
                  </label>
                  <span class="app-pick__count">已选 {{ selectedKeys.length }} 只</span>
                </div>

                <div v-for="row in rows" :key="row.varietyKey" class="app-pending">
                  <label class="app-pending__pick">
                    <input
                      type="checkbox"
                      :checked="selectedKeys.includes(row.varietyKey ?? '')"
                      @change="toggle(row.varietyKey ?? '')"
                    />
                  </label>
                  <div class="app-pending__body">
                    <div class="app-pending__head">
                      <b>{{ row.varietyCode }} {{ row.varietyName }}</b>
                      <span class="app-pending__type">{{ row.varietyType }}</span>
                      <span v-if="row.confirmed === false" class="app-pending__badge">未确认</span>
                    </div>
                    <p class="app-pending__why">{{ reasonOf(row) }}</p>
                    <p v-if="hintsOf(row).length" class="app-pending__hint">
                      名称命中：{{ hintsOf(row).join('、') }}
                    </p>
                  </div>
                </div>
              </AppGroup>

              <AppGroup
                v-if="rows.length"
                title="指定类型"
                code="ASSIGN"
                :meta="selectedKeys.length ? `将作用于 ${selectedKeys.length} 只` : '请先勾选'"
                :meta-ok="selectedKeys.length > 0"
              >
                <template #icon>
                  <span v-html="ICONS.tag" />
                </template>

                <AppField label="基金类型">
                  <select v-model="category" class="app-select">
                    <option value="">— 选择类型 —</option>
                    <option v-for="item in CATEGORIES" :key="item.key" :value="item.key">
                      {{ item.label }}
                    </option>
                  </select>
                </AppField>

                <AppField
                  label="跟踪指数代码"
                  :description="
                    category === 'DIVIDEND'
                      ? '红利必填，用于查股息率；不填保存会被拒绝'
                      : '可选。填了之后该基金按此指数取股息率与代理行情'
                  "
                >
                  <AppInput v-model="trackIndexCode" :numeric="true" />
                </AppField>

                <AppField label="分红方式" description="红利再投时现金分红按 0 计，会影响收益核算">
                  <select v-model="dividendMethod" class="app-select">
                    <option value="">— 保持原值 —</option>
                    <option value="CASH">现金分红</option>
                    <option value="REINVEST">红利再投</option>
                  </select>
                </AppField>

                <p v-if="formError" class="app-formerror">{{ formError }}</p>

                <div class="app-actions">
                  <AppButton
                    :disabled="saving || !selectedKeys.length || !category"
                    @click="submit"
                  >
                    {{ saving ? '保存中…' : `保存到 ${selectedKeys.length} 只` }}
                  </AppButton>
                </div>

                <p class="app-hint">
                  保存后这些标的的 <code>confirmed</code> 会置为已确认，不再出现在本清单里。
                  黄金与红利会立刻影响预估分支与策略提醒，债券会影响债券监控。
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
 * 待确认类型页：列出推断不出来的标的，批量指定类型与跟踪指数
 */
import { computed, onMounted, ref } from 'vue'
import AppAlert from '@/ui/AppAlert.vue'
import AppButton from '@/ui/AppButton.vue'
import AppField from '@/ui/AppField.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppInput from '@/ui/AppInput.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import { list as varietyList, save as varietySave } from '@/api/varietyController'
import { ElMessage } from '@/ui/message'

/** 内联图标 */
const ICONS: Record<string, string> = {
  flag: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M6 21V4M6 4h11l-2.5 4.5L17 13H6"/></svg>',
  tag: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12V5a2 2 0 0 1 2-2h7l9 9-9 9z"/><circle cx="8" cy="8" r="1.4"/></svg>',
}

/** 可选类型，UNKNOWN 不在其中 */
const CATEGORIES = [
  { key: 'STOCK', label: '股票型' },
  { key: 'MIXED', label: '混合型' },
  { key: 'BOND', label: '债券型' },
  { key: 'GOLD', label: '黄金' },
  { key: 'DIVIDEND', label: '红利' },
  { key: 'ETF', label: '场内 ETF' },
] as const

const loading = ref(true)
const saving = ref(false)
const errorText = ref('')
const formError = ref('')
const holdingOnly = ref(true)
const rows = ref<API.VarietyVO[]>([])
const selectedKeys = ref<string[]>([])
const category = ref('')
const trackIndexCode = ref('')
const dividendMethod = ref('')

/** 是否全选 */
const allSelected = computed(
  () => rows.value.length > 0 && selectedKeys.value.length === rows.value.length,
)

/**
 * 拉待确认清单：category=UNKNOWN 即推断不出来的那些
 *
 * @returns 无
 */
async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  selectedKeys.value = []
  try {
    const result = await varietyList({ category: 'UNKNOWN' })
    const all = result.data ?? []
    if (holdingOnly.value) {
      // 只有持仓才会影响预估与策略，未持仓的不急着确认
      const holdingKeys = await holdingKeySet()
      rows.value = all.filter((row) => row.varietyKey && holdingKeys.has(row.varietyKey))
    } else {
      rows.value = all.filter((row) => row.varietyKey)
    }
  } catch {
    errorText.value = '读取待确认清单失败，请确认已登录'
    rows.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 取持有中的品种主键集合
 *
 * @returns 业务主键集合
 */
async function holdingKeySet(): Promise<Set<string>> {
  const { overview } = await import('@/api/holdingController')
  const result = await overview()
  const set = new Set<string>()
  ;(result.data?.items ?? []).forEach((item) => {
    if (item.varietyKey) {
      set.add(item.varietyKey)
    }
  })
  return set
}

/**
 * 勾选或取消单个标的
 *
 * @param key 品种业务主键
 * @returns 无
 */
function toggle(key: string): void {
  if (!key) {
    return
  }
  const index = selectedKeys.value.indexOf(key)
  if (index >= 0) {
    selectedKeys.value.splice(index, 1)
  } else {
    selectedKeys.value.push(key)
  }
}

/**
 * 全选或全不选
 *
 * @param checked 是否全选
 * @returns 无
 */
function toggleAll(checked: boolean): void {
  selectedKeys.value = checked ? rows.value.map((row) => row.varietyKey ?? '').filter(Boolean) : []
}

/**
 * 批量保存，逐个调 save 接口；失败时统计并提示
 *
 * @returns 无
 */
async function submit(): Promise<void> {
  formError.value = ''
  if (!category.value) {
    formError.value = '请先选择基金类型'
    return
  }
  if (category.value === 'DIVIDEND' && !trackIndexCode.value.trim()) {
    // 红利没跟踪指数就取不到股息率，后端也会拒绝，这里先拦一次省一次请求
    formError.value = '红利类型必须填写跟踪指数代码，否则取不到股息率'
    return
  }
  saving.value = true
  let ok = 0
  let failed = 0
  try {
    for (const key of selectedKeys.value) {
      const row = rows.value.find((item) => item.varietyKey === key)
      if (!row) {
        continue
      }
      try {
        await varietySave({
          varietyKey: key,
          varietyType: row.varietyType,
          varietyCode: row.varietyCode,
          varietyName: row.varietyName,
          category: category.value as API.VarietyVO['category'],
          trackIndexCode: trackIndexCode.value.trim() || undefined,
          dividendMethod: (dividendMethod.value || undefined) as API.VarietyVO['dividendMethod'],
          confirmed: true,
        })
        ok++
      } catch {
        failed++
      }
    }
    if (ok > 0) {
      ElMessage.success(failed > 0 ? `已保存 ${ok} 只，${failed} 只失败` : `已保存 ${ok} 只`)
      await load()
    } else {
      formError.value = '全部保存失败，请查看接口返回的原因'
    }
  } finally {
    saving.value = false
  }
}

/**
 * 推断不出来的原因，用名称与类型给出可读说明
 *
 * @param row 品种
 * @returns 原因
 */
function reasonOf(row: API.VarietyVO): string {
  const name = row.varietyName ?? ''
  if (name.includes('黄金') && (name.includes('黄金股') || name.includes('金矿'))) {
    return '名称同时含「黄金」与「黄金股/金矿」类词，无法判断跟踪金价还是黄金股'
  }
  if (name.includes('红利') || name.includes('股息')) {
    return '名称含红利关键词，请确认类型并填写跟踪指数代码'
  }
  return '上游类型与名称都不足以确定，请手工指定'
}

/**
 * 列出名称里命中的关键词，供判断依据
 *
 * @param row 品种
 * @returns 命中的词
 */
function hintsOf(row: API.VarietyVO): string[] {
  const name = row.varietyName ?? ''
  const keywords = [
    '黄金',
    '贵金属',
    '上海金',
    '黄金股',
    '黄金产业',
    '金矿',
    '红利',
    '股息',
    '改革',
    '混改',
  ]
  return keywords.filter((word) => name.includes(word))
}

onMounted(load)
</script>

<style scoped>
/* 骨架类来自全局 styles/layout.css */
.app-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--t2);
}

.app-select {
  height: 30px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid var(--hair);
  background: var(--inp-bg);
  color: var(--t1);
  font-size: 12.5px;
}

.app-pick {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--hair);
}

.app-pick__count {
  font-size: 12px;
  color: var(--t3);
}

.app-pending {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--hair);
}

.app-pending:last-child {
  border-bottom: none;
}

.app-pending__pick {
  padding-top: 2px;
}

.app-pending__body {
  min-width: 0;
}

.app-pending__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--t1);
}

.app-pending__type {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--group-bg);
  color: var(--t3);
  font-size: 11.5px;
}

.app-pending__badge {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--dn-soft);
  color: var(--warn);
  font-size: 11.5px;
}

.app-pending__why {
  margin: 5px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--t2);
}

.app-pending__hint {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--t3);
}

.app-formerror {
  margin: 10px 0 0;
  font-size: 12.5px;
  color: var(--warn);
}

.app-actions {
  margin-top: 14px;
}
</style>
