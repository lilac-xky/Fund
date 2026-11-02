<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <AppTopBar active="screenshot">
      <template #status>
        {{
          importing
            ? '正在识别…'
            : orders.length
              ? `${orders.length} 条待确认`
              : `${shots.length} 张截图`
        }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimable-count="orders.filter((row) => row.status === '可导入').length"
      :total-count="orders.length"
      :market-mood="orders.length ? '待人工确认' : current ? '待人工核对' : '未选择截图'"
      :next-note="orders.length ? `已选 ${selectedSeqs.length} 条` : (current?.fileName ?? '')"
    />

    <div class="app-body">
      <div class="app-stage app-stage--no-aside">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                持仓 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">截图核对</b>
              </div>
              <div class="app-eyebrow">Screenshot</div>
              <h1 class="app-h1">账单导入与截图核对</h1>
              <p class="app-lede">
                传支付宝「交易记录明细」导出的文件，自动识别买入、卖出、分红与转换，逐条确认后再入库；
                也可以传一张持仓截图，与系统里的数字并排看一眼。截图只做留存，不做识别。
              </p>
            </div>

            <AppAlert kind="info">
              账单里的<strong>退款会抵消同一笔买入</strong>，转换退回余额宝的找零会
              <strong>并入转出金额</strong>；余额宝收益不算基金申赎会被跳过。
              分红只留档、不计入持仓与收益，避免与系统自动推算的除息重复。
            </AppAlert>

            <!-- 账单导入：识别结果逐条可改，确认后才写库 -->
            <AppGroup title="导入账单" code="IMPORT" meta="支付宝导出的交易记录明细，CSV 或 TXT">
              <template #icon>
                <span v-html="ICONS.import" />
              </template>
              <div class="app-upload">
                <input
                  ref="billInput"
                  type="file"
                  accept=".csv,.txt,text/csv,text/plain"
                  class="app-upload__input"
                  @change="onPickBill"
                />
                <AppButton :disabled="importing" @click="billInput?.click()">
                  {{ importing ? '识别中…' : '选择账单文件' }}
                </AppButton>
                <span v-if="billName" class="app-upload__name">{{ billName }}</span>
              </div>
              <p v-if="importError" class="app-upload__error">{{ importError }}</p>
              <p v-else class="app-hint">
                支付宝网页版 → 交易记录 → 选日期范围 → 下载，拿到「账单明细」那个文件即可。
                识别只读不写，确认前不会改动任何持仓。
              </p>
            </AppGroup>

            <AppGroup
              v-if="orders.length"
              title="待确认"
              code="CONFIRM"
              :meta="`${selectedSeqs.length} / ${orders.length} 已选`"
            >
              <template #icon>
                <span v-html="ICONS.compare" />
              </template>

              <div class="app-importbar">
                <label class="app-check">
                  <input
                    ref="selectAllBox"
                    type="checkbox"
                    :checked="allSelected"
                    :disabled="!selectableInView.length"
                    @change="toggleAll(($event.target as HTMLInputElement).checked)"
                  />
                  <span>全选当前筛选（{{ selectableInView.length }} 条）</span>
                </label>
                <AppButton size="sm" :disabled="importing" @click="billInput?.click()">
                  重新选账单
                </AppButton>
                <AppButton
                  v-if="selectedSeqs.length"
                  size="sm"
                  :disabled="submitting"
                  @click="clearSelection"
                >
                  清空勾选
                </AppButton>
                <span class="app-importbar__note">
                  共 {{ orders.length }} 条，当前筛选出 {{ filteredOrders.length }} 条； 已勾选
                  {{ selectedSeqs.length }} 条
                  <template v-if="selectedSeqs.length > selectedInView">
                    （含其他筛选下已选的 {{ selectedSeqs.length - selectedInView }} 条）
                  </template>
                </span>
              </div>

              <!-- 账单动辄上百条，先按基金与动作缩小到要看的那几条 -->
              <div class="app-importbar">
                <label class="app-check">
                  <span>基金</span>
                  <select v-model="filterFund" class="app-select">
                    <option value="">全部基金</option>
                    <option v-for="name in fundNames" :key="name" :value="name">{{ name }}</option>
                  </select>
                </label>
                <label class="app-check">
                  <span>动作</span>
                  <select v-model="filterAction" class="app-select">
                    <option value="">全部动作</option>
                    <option v-for="item in actionOptions" :key="item.value" :value="item.value">
                      {{ item.label }}
                    </option>
                  </select>
                </label>
                <label class="app-check">
                  <input v-model="onlyImportable" type="checkbox" />
                  <span>只看可导入</span>
                </label>
                <label class="app-check">
                  <input v-model="hideImported" type="checkbox" />
                  <span>隐藏已导入（{{ importedCount }} 条）</span>
                </label>
                <AppButton size="sm" @click="clearFilter">清除筛选</AppButton>
              </div>

              <div v-if="!filteredOrders.length" class="app-listempty">
                当前筛选条件下没有记录，换个基金或清除筛选。
              </div>

              <div v-else class="app-orders">
                <div
                  v-for="row in filteredOrders"
                  :key="row.seq"
                  class="app-order"
                  :class="{ 'app-order--off': !selectedSeqs.includes(row.seq ?? -1) }"
                >
                  <label class="app-order__check">
                    <input
                      type="checkbox"
                      :checked="selectedSeqs.includes(row.seq ?? -1)"
                      :disabled="!row.selectable"
                      @change="toggleRow(row)"
                    />
                  </label>
                  <div class="app-order__main">
                    <div class="app-order__top">
                      <b class="app-order__name">{{ row.fundName || '（未识别）' }}</b>
                      <span class="app-order__action">{{ row.actionLabel }}</span>
                      <span class="app-order__amount">{{ row.amount }} 元</span>
                      <span class="app-order__status">{{ row.status }}</span>
                    </div>
                    <div class="app-order__meta">
                      <span>下单 {{ row.orderTime ?? '—' }}</span>
                      <span>确认日 {{ row.confirmDate ?? '—' }}</span>
                      <span v-if="row.fundCode">代码 {{ row.fundCode }}</span>
                      <span v-if="row.tradeNo" class="app-order__trade"
                        >交易号 {{ row.tradeNo }}</span
                      >
                    </div>
                    <p v-if="row.reason" class="app-order__reason">{{ row.reason }}</p>

                    <!-- 多候选：让使用者自己选，不替猜 A/C 类 -->
                    <div v-if="(row.candidates?.length ?? 0) > 1" class="app-order__pick">
                      <span>选择基金</span>
                      <select
                        class="app-select"
                        :value="chosenCode(row)"
                        @change="pickCandidate(row, ($event.target as HTMLSelectElement).value)"
                      >
                        <option value="">请选择</option>
                        <option
                          v-for="candidate in row.candidates"
                          :key="candidate.fundCode"
                          :value="candidate.fundCode"
                        >
                          {{ candidate.fundName }}（{{ candidate.fundCode }}）
                          {{ candidate.registered ? '' : '· 未注册' }}
                        </option>
                      </select>
                    </div>

                    <!-- 未注册：选中候选后可就地确认身份，不必跳去持仓页 -->
                    <div
                      v-if="row.fundCode && !candidateOf(row)?.registered"
                      class="app-order__pick"
                    >
                      <span>该基金未注册</span>
                      <AppButton
                        size="sm"
                        :disabled="registering.has(row.seq ?? -1)"
                        @click="register(row)"
                      >
                        {{ registering.has(row.seq ?? -1) ? '确认中…' : '确认身份' }}
                      </AppButton>
                      <span class="app-order__reason">确认后即可勾选导入</span>
                    </div>

                    <!-- 转换拆成两行：这行管转出的卖出，转入侧由 #IN 那行管，避免买两遍 -->
                    <div v-if="row.action === 'CONVERT'" class="app-order__fill">
                      <p class="app-order__reason">
                        {{
                          `转出按 ${transferOutOf(row)} 元记账（账单净额${refundOf(row) > 0 ? ` ${row.amount} 元 + 找零 ${refundOf(row)} 元` : ''}），份额按确认日净值反推；转入侧「${row.targetFundName ?? '转入基金'}」按账单金额买入，两行一起勾选即可`
                        }}
                      </p>
                    </div>
                  </div>
                </div>
              </div>

              <div class="app-actions">
                <AppButton
                  variant="primary"
                  size="lg"
                  :disabled="!selectedSeqs.length || submitting"
                  @click="onSubmit"
                >
                  {{ submitting ? '提交中…' : `确认导入 ${selectedSeqs.length} 条` }}
                </AppButton>
                <span v-if="submitNote" class="app-order__reason">{{ submitNote }}</span>
              </div>
            </AppGroup>

            <!-- 红利再投在账单里没有独立记录，只能对着分红流水手工补一批次 -->
            <AppGroup
              title="补录红利再投"
              code="REINVEST"
              meta="账单不记红利再投，按支付宝的「份额确认」补一批"
            >
              <template #icon>
                <span v-html="ICONS.reinvest" />
              </template>

              <div class="app-importbar">
                <label class="app-check">
                  <span>基金</span>
                  <select v-model="reinvestForm.varietyKey" class="app-select">
                    <option value="">请选择已注册的基金</option>
                    <option
                      v-for="item in varietyOptions"
                      :key="item.varietyKey"
                      :value="item.varietyKey"
                    >
                      {{ item.varietyName }}（{{ item.varietyCode }}）
                    </option>
                  </select>
                </label>
                <label class="app-check">
                  <span>份额</span>
                  <AppInput
                    :model-value="reinvestForm.shares"
                    :numeric="true"
                    inputmode="decimal"
                    placeholder="1.78"
                    @update:model-value="reinvestForm.shares = $event"
                  />
                </label>
                <label class="app-check">
                  <span>除息日</span>
                  <input
                    :value="reinvestForm.date"
                    type="date"
                    class="app-date"
                    @input="reinvestForm.date = ($event.target as HTMLInputElement).value"
                  />
                </label>
                <AppButton
                  variant="primary"
                  :disabled="
                    reinvesting || !reinvestForm.varietyKey || !(Number(reinvestForm.shares) > 0)
                  "
                  @click="onReinvest"
                >
                  {{ reinvesting ? '补录中…' : '补录这一笔' }}
                </AppButton>
              </div>
              <p v-if="reinvestNote" class="app-order__reason">{{ reinvestNote }}</p>
              <p v-else class="app-hint">
                红利再投是「份额确认」，账单交易记录里不会出现。按支付宝里的份额与确认日补一笔买入即可，
                成本价取除息日净值，分红收益由系统按除息记录自动推算，这里只补份额。
              </p>
            </AppGroup>

            <AppGroup title="上传截图" code="UPLOAD" meta="png / jpg / webp，≤ 10MB">
              <template #icon>
                <span v-html="ICONS.upload" />
              </template>
              <div class="app-upload">
                <input
                  ref="fileInput"
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  class="app-upload__input"
                  @change="onPick"
                />
                <AppButton :disabled="uploading" @click="fileInput?.click()">
                  {{ uploading ? '上传中…' : '选择图片' }}
                </AppButton>
                <span v-if="pickedName" class="app-upload__name">{{ pickedName }}</span>
              </div>
              <p v-if="uploadError" class="app-upload__error">{{ uploadError }}</p>
              <p v-else class="app-hint">
                选图后立刻上传留存，文件名按上传时间生成；原图不改写、不压缩。
              </p>
            </AppGroup>

            <AppGroup title="已留存" code="LIST" :meta="`${shots.length} 张`">
              <template #icon>
                <span v-html="ICONS.gallery" />
              </template>

              <div v-if="loading" class="app-loading">正在读取截图列表…</div>
              <AppAlert v-else-if="loadError" kind="error">{{ loadError }}</AppAlert>
              <div v-else-if="!shots.length" class="app-listempty">
                还没有截图。点上面的「选择图片」传一张，就能在这里对照。
              </div>

              <div v-else class="app-shots">
                <button
                  v-for="shot in shots"
                  :key="shot.fileName"
                  type="button"
                  class="app-shots__item"
                  :class="{ 'app-shots__item--on': shot.fileName === current?.fileName }"
                  @click="select(shot)"
                >
                  <b>{{ shot.fileName }}</b>
                  <span>{{ sizeText(shot.size) }} · {{ shortTime(shot.uploadedAt) }}</span>
                </button>
              </div>
            </AppGroup>

            <AppGroup
              v-if="current"
              title="并排核对"
              code="CHECK"
              :meta="`${check?.rows?.length ?? 0} 只待比对`"
            >
              <template #icon>
                <span v-html="ICONS.compare" />
              </template>

              <div v-if="checking" class="app-loading">正在读取核对数据…</div>
              <AppAlert v-else-if="checkError" kind="error">{{ checkError }}</AppAlert>

              <div v-else class="app-compare">
                <div class="app-compare__left">
                  <p class="app-compare__cap">截图</p>
                  <a
                    v-if="rawUrl && !imageError"
                    class="app-compare__img app-compare__img--link"
                    :href="rawUrl"
                    target="_blank"
                    rel="noopener"
                    title="点击查看原图"
                  >
                    <img
                      class="app-compare__pic"
                      :src="rawUrl"
                      :alt="current.fileName ?? '持仓截图'"
                      @error="onImageError"
                    />
                  </a>
                  <div v-else class="app-compare__img">
                    <span>{{ imageError ? '图片读取失败' : '未选择截图' }}</span>
                    <span v-if="imageError" class="app-compare__tip">
                      刷新列表再试；仍失败请确认文件还在 data/screenshot 下
                    </span>
                  </div>
                </div>

                <div class="app-compare__right">
                  <p class="app-compare__cap">已录入</p>
                  <table class="app-table">
                    <thead>
                      <tr>
                        <th>代码 / 名称</th>
                        <th class="app-table__num">份额</th>
                        <th class="app-table__num">成本价</th>
                        <th class="app-table__num">市值</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="row in check?.rows ?? []" :key="row.varietyKey">
                        <td>
                          <b>{{ row.varietyCode }}</b>
                          <span class="app-table__name">{{ row.varietyName }}</span>
                          <div v-if="row.hints?.length" class="app-table__hint">
                            {{ row.hints.join(' · ') }}
                          </div>
                        </td>
                        <td class="app-table__num">{{ num(row.shares) }}</td>
                        <td class="app-table__num">{{ num(row.avgCostPrice) }}</td>
                        <td class="app-table__num">{{ num(row.marketValue) }}</td>
                      </tr>
                    </tbody>
                  </table>
                  <p v-if="check?.notice" class="app-compare__notice">{{ check.notice }}</p>
                </div>
              </div>
            </AppGroup>
          </div>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 账单导入与持仓截图核对页：账单识别成待确认行供逐条确认入库，截图留存供人工并排比对
 */
import { computed, onMounted, reactive, ref, watchEffect } from 'vue'
import AppAlert from '@/ui/AppAlert.vue'
import AppButton from '@/ui/AppButton.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppInput from '@/ui/AppInput.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import {
  check as fetchCheck,
  list as fetchList,
  upload as uploadShot,
} from '@/api/screenshotController'
import { preview as previewBill } from '@/api/alipayImportController'
import { confirm as confirmCandidate } from '@/api/fundController'
import { buy as buyHolding, sell as sellHolding } from '@/api/holdingController'
import { list as listVariety } from '@/api/varietyController'
import { CONTEXT_PATH } from '@/request'
import { shortTime } from '@/utils/format'
import { ElMessage } from '@/ui/message'

/** 内联图标 */
const ICONS: Record<string, string> = {
  upload:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V4M8 8l4-4 4 4"/><path d="M4 15v3.5A1.5 1.5 0 0 0 5.5 20h13a1.5 1.5 0 0 0 1.5-1.5V15"/></svg>',
  gallery:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8.5" cy="9.5" r="1.6"/><path d="m4 17 5-5 4.5 4.5L17 13l3 3"/></svg>',
  compare:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="2.5" y="5" width="8" height="14" rx="1.6"/><rect x="13.5" y="5" width="8" height="14" rx="1.6"/></svg>',
  import:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h4"/></svg>',
  reinvest:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M20 12a8 8 0 1 1-2.6-5.9"/><path d="M20 4v5h-5"/><path d="M12 8v4.5l3 1.8"/></svg>',
}

/** 后端允许的扩展名，与 ScreenshotServiceImpl 保持一致 */
const ALLOWED_EXT = ['png', 'jpg', 'jpeg', 'webp']

/** 后端上传上限，10MB */
const MAX_BYTES = 10 * 1024 * 1024

const loading = ref(true)
const uploading = ref(false)
const checking = ref(false)
const pickedName = ref('')
const loadError = ref('')
const uploadError = ref('')
const checkError = ref('')
const imageError = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const shots = ref<API.ScreenshotVO[]>([])
const current = ref<API.ScreenshotVO | null>(null)
const check = ref<API.ScreenshotCheckVO | null>(null)

/** 账单导入状态 */
const billInput = ref<HTMLInputElement | null>(null)
const billName = ref('')
const importing = ref(false)
const submitting = ref(false)
const importError = ref('')
const submitNote = ref('')
const orders = ref<API.AlipayImportRow[]>([])
const selectedSeqs = ref<number[]>([])

/** 正在确认身份的行的序号 */
const registering = ref(new Set<number>())

/** 红利再投补录表单 */
const reinvestForm = reactive({ varietyKey: '', shares: '', date: '' })
const reinvesting = ref(false)
const reinvestNote = ref('')
const varieties = ref<API.VarietyVO[]>([])

/** 已被确认的品种选项，补录红利再投时从这里挑 */
const varietyOptions = computed(() =>
  [...varieties.value].sort((left, right) =>
    (left.varietyName ?? '').localeCompare(right.varietyName ?? '', 'zh-CN'),
  ),
)

/** 筛选条件：基金名、动作、只看可导入、隐藏已导入 */
const filterFund = ref('')
const filterAction = ref('')
const onlyImportable = ref(false)

/** 上次已导入过的行默认藏起来，避免以为要重导一遍 */
const hideImported = ref(true)

/** 已导入条数，供开关回显 */
const importedCount = computed(() => orders.value.filter((row) => row.status === '已导入').length)

/** 可选的基金名，去重后按名称排序 */
const fundNames = computed(() =>
  [
    ...new Set(orders.value.map((row) => row.fundName).filter((name): name is string => !!name)),
  ].sort((left, right) => left.localeCompare(right, 'zh-CN')),
)

/** 可选的动作品类，只列出账单里实际出现的 */
const actionOptions = computed(() =>
  [...new Set(orders.value.map((row) => row.action).filter((action): action is string => !!action))]
    .map((action) => ({
      value: action,
      label: orders.value.find((row) => row.action === action)?.actionLabel ?? action,
    }))
    .sort((left, right) => left.label.localeCompare(right.label, 'zh-CN')),
)

/** 按筛选条件过滤后的行 */
const filteredOrders = computed(() =>
  orders.value.filter((row) => {
    if (hideImported.value && row.status === '已导入') {
      return false
    }
    if (filterFund.value && row.fundName !== filterFund.value) {
      return false
    }
    if (filterAction.value && row.action !== filterAction.value) {
      return false
    }
    return !(onlyImportable.value && !row.selectable)
  }),
)

/** 当前筛选结果里可勾选的行，全选与勾选态都只看这一批 */
const selectableInView = computed(() =>
  filteredOrders.value.filter((row) => row.selectable && row.seq != null),
)

/** 当前筛选结果里已勾选的行数 */
const selectedInView = computed(() => {
  const seqs = new Set(selectableInView.value.map((row) => row.seq as number))
  return selectedSeqs.value.filter((seq) => seqs.has(seq)).length
})

/** 当前筛选结果是否已全选，筛选后不会再拿全量去比 */
const allSelected = computed(
  () => selectableInView.value.length > 0 && selectedInView.value === selectableInView.value.length,
)

/** 全选框的半选态：勾了一部分 */
const partlySelected = computed(
  () => selectedInView.value > 0 && selectedInView.value < selectableInView.value.length,
)

/** 全选框元素，半选态只能改 DOM 属性，没有对应 attribute */
const selectAllBox = ref<HTMLInputElement | null>(null)

// 半选态必须写 DOM，模板里没有 :indeterminate 这种绑定方式
watchEffect(() => {
  if (selectAllBox.value) {
    selectAllBox.value.indeterminate = partlySelected.value
  }
})

/** 核对请求序号，连续点选时只认最后一次的响应 */
let checkSeq = 0

/** 图片直链，img 标签直接取字节；带 Cookie 的同源请求，不经过 JSON 封装的 request */
const rawUrl = computed(() => {
  const fileName = current.value?.fileName
  return fileName ? `${CONTEXT_PATH}/screenshot/raw?fileName=${encodeURIComponent(fileName)}` : ''
})

/**
 * 读取已确认的品种，供红利再投补录下拉使用
 *
 * @returns 无
 */
async function loadVarieties(): Promise<void> {
  try {
    const result = await listVariety({ confirmed: true })
    varieties.value = (result.data ?? []).filter((item) => item.varietyKey && item.varietyName)
  } catch {
    // 品种列表只是补录用的下拉，取不到不影响账单导入
    varieties.value = []
  }
}

/**
 * 读取已留存列表并选中一张
 *
 * @param prefer 优先选中的文件名，缺省时保持当前选中或选最新一张
 * @returns 无
 */
async function load(prefer?: string): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const result = await fetchList({ silent: true })
    shots.value = result.data ?? []
    const keep = prefer ?? current.value?.fileName
    const next =
      (keep ? shots.value.find((shot) => shot.fileName === keep) : undefined) ?? shots.value[0]
    if (next) {
      await select(next)
    } else {
      current.value = null
      check.value = null
    }
  } catch (error) {
    shots.value = []
    current.value = null
    check.value = null
    loadError.value = `截图列表读取失败：${messageOf(error)}`
  } finally {
    loading.value = false
  }
}

/**
 * 选中一张截图并读取核对视图
 *
 * @param shot 截图
 * @returns 无
 */
async function select(shot: API.ScreenshotVO): Promise<void> {
  const seq = ++checkSeq
  current.value = shot
  check.value = null
  checkError.value = ''
  imageError.value = false
  checking.value = true
  try {
    const result = await fetchCheck({ fileName: shot.fileName ?? '' }, { silent: true })
    // 连续点选时旧响应可能晚到，不能覆盖新选中那张的核对表
    if (seq !== checkSeq) {
      return
    }
    check.value = result.data ?? null
  } catch (error) {
    if (seq !== checkSeq) {
      return
    }
    checkError.value = `核对数据读取失败：${messageOf(error)}`
  } finally {
    if (seq === checkSeq) {
      checking.value = false
    }
  }
}

/**
 * 选中文件后先做本地校验再上传
 *
 * @param event 选择事件
 * @returns 无
 */
async function onPick(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 清空 value，同一张图再选一次也能触发 change
  input.value = ''
  if (!file) {
    return
  }
  pickedName.value = file.name
  uploadError.value = ''
  const ext = extensionOf(file.name)
  if (!ALLOWED_EXT.includes(ext)) {
    uploadError.value = `只支持 png / jpg / jpeg / webp，当前是 ${ext || '无扩展名'}`
    return
  }
  if (file.size > MAX_BYTES) {
    uploadError.value = `图片不能超过 10MB，当前 ${sizeText(file.size)}`
    return
  }
  await uploadPicked(file)
}

/**
 * 上传文件并刷新列表，选中刚留存的那张
 *
 * @param file 已通过本地校验的文件
 * @returns 无
 */
async function uploadPicked(file: File): Promise<void> {
  uploading.value = true
  try {
    const result = await uploadShot({}, file, { silent: true })
    const saved = result.data
    ElMessage.success(saved?.fileName ? `已留存 ${saved.fileName}` : '截图已留存')
    pickedName.value = ''
    await load(saved?.fileName)
  } catch (error) {
    uploadError.value = `上传失败：${messageOf(error)}`
  } finally {
    uploading.value = false
  }
}

/**
 * 图片读不到时给出兜底文案，不显示破图
 *
 * @returns 无
 */
function onImageError(): void {
  imageError.value = true
}

/**
 * 选中账单文件后上传识别，结果只进内存不写库
 *
 * @param event 选择事件
 * @returns 无
 */
async function onPickBill(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 清空 value，同一个文件再选一次也能触发 change
  input.value = ''
  if (!file) {
    return
  }
  billName.value = file.name
  importError.value = ''
  submitNote.value = ''
  importing.value = true
  try {
    const result = await previewBill({}, file, { silent: true })
    orders.value = result.data ?? []
    // 默认只勾上识别完且能直接落库的行，其余留给人处理
    selectedSeqs.value = orders.value
      .filter((row) => row.selectable && row.seq != null)
      .map((row) => row.seq as number)
    if (!orders.value.length) {
      importError.value = '账单里没有识别到基金申赎记录，确认选的是「交易记录明细」文件'
    } else {
      ElMessage.success(`识别出 ${orders.value.length} 条，请逐条确认`)
    }
  } catch (error) {
    orders.value = []
    selectedSeqs.value = []
    importError.value = `识别失败：${messageOf(error)}`
  } finally {
    importing.value = false
  }
}

/**
 * 清除筛选，回到全部记录
 *
 * @returns 无
 */
function clearFilter(): void {
  filterFund.value = ''
  filterAction.value = ''
  onlyImportable.value = false
  hideImported.value = true
}

/**
 * 取该行当前选中的候选
 *
 * @param row 待确认行
 * @returns 候选，未选返回 undefined
 */
function candidateOf(row: API.AlipayImportRow): API.Candidate | undefined {
  return (row.candidates ?? []).find((item) => item.fundCode === row.fundCode)
}

/**
 * 取转换行退回余额宝的找零
 *
 * @param row 待确认行
 * @returns 找零金额
 */
function refundOf(row: API.AlipayImportRow): number {
  return Number(row.refundAmount ?? 0)
}

/**
 * 取转换行的转出所得，缺字段时退回账单金额
 *
 * @param row 待确认行
 * @returns 转出侧记账金额
 */
function transferOutOf(row: API.AlipayImportRow): number {
  return Number(row.transferOutAmount ?? row.amount ?? 0)
}

/**
 * 就地确认身份：把候选的基金注册成一个品种，成功后该行即可勾选
 *
 * @param row 待确认行
 * @returns 无
 */
async function register(row: API.AlipayImportRow): Promise<void> {
  const code = row.fundCode
  const seq = row.seq
  if (!code || seq == null) {
    return
  }
  registering.value = new Set([...registering.value, seq])
  try {
    // 类型留空由后端按已注册记录、场内身份、号段与名称判定
    const result = await confirmCandidate({ code })
    const variety = result.data
    const candidate = candidateOf(row)
    if (candidate) {
      candidate.registered = true
      candidate.varietyKey = variety?.varietyKey
    }
    row.varietyKey = variety?.varietyKey
    row.fundName = variety?.varietyName ?? row.fundName
    row.status = '可导入'
    row.reason = '已确认身份，可以导入'
    row.selectable = true
    selectedSeqs.value = [...selectedSeqs.value, seq]
    ElMessage.success(`已注册 ${variety?.varietyName ?? code}`)
  } catch (error) {
    submitNote.value = `确认身份失败：${messageOf(error)}`
  } finally {
    const next = new Set(registering.value)
    next.delete(seq)
    registering.value = next
  }
}

/**
 * 多候选行当前选中的代码，供下拉回显
 *
 * @param row 待确认行
 * @returns 6 位代码，未选返回空串
 */
function chosenCode(row: API.AlipayImportRow): string {
  return row.fundCode ?? ''
}

/**
 * 勾选或取消一行
 *
 * @param row 待确认行
 * @returns 无
 */
function toggleRow(row: API.AlipayImportRow): void {
  const seq = row.seq
  if (seq == null || !row.selectable) {
    return
  }
  selectedSeqs.value = selectedSeqs.value.includes(seq)
    ? selectedSeqs.value.filter((item) => item !== seq)
    : [...selectedSeqs.value, seq]
}

/**
 * 全选或全不选当前筛选结果里可导入的行，只动这一批
 *
 * <p>筛选出 17 条时点全选，选中的就该是这 17 条；若按全量去选，用户以为只导了看得见的那些，
 * 实际会把 170 条全提交，这是会造成数据污染的错误。</p>
 *
 * @param checked 是否全选
 * @returns 无
 */
function toggleAll(checked: boolean): void {
  const inView = selectableInView.value.map((row) => row.seq as number)
  if (checked) {
    // 保留其他筛选下已勾选的，再并入本屏这批，避免切换筛选时丢失已选
    selectedSeqs.value = [...new Set([...selectedSeqs.value, ...inView])]
    return
  }
  const drop = new Set(inView)
  selectedSeqs.value = selectedSeqs.value.filter((seq) => !drop.has(seq))
}

/**
 * 清空全部勾选，跨筛选保留时用它一键归零
 *
 * @returns 无
 */
function clearSelection(): void {
  selectedSeqs.value = []
}

/**
 * 多候选行选定一只基金，选定后该行才可勾选
 *
 * @param row       待确认行
 * @param fundCode  选中的 6 位代码
 * @returns 无
 */
function pickCandidate(row: API.AlipayImportRow, fundCode: string): void {
  const candidate = (row.candidates ?? []).find((item) => item.fundCode === fundCode)
  row.fundCode = candidate?.fundCode
  row.varietyKey = candidate?.varietyKey
  // 选到已注册的那只才能提交，未注册的仍要先回去确认身份
  row.selectable = Boolean(candidate?.registered)
  if (!row.selectable && row.seq != null) {
    selectedSeqs.value = selectedSeqs.value.filter((seq) => seq !== row.seq)
  }
}

/**
 * 提交勾选的行，按确认日从早到晚、同日先买后卖
 *
 * @returns 无
 */
async function onSubmit(): Promise<void> {
  const picked = orderedForSubmit(
    orders.value.filter((row) => row.seq != null && selectedSeqs.value.includes(row.seq)),
  )
  if (!picked.length) {
    return
  }
  submitting.value = true
  submitNote.value = ''
  const failed: string[] = []
  const doneSeqs = new Set<number>()
  for (const row of picked) {
    try {
      await submitRow(row)
      if (row.seq != null) {
        doneSeqs.add(row.seq)
      }
    } catch (error) {
      // 逐条提交，一条失败不该让整批停下，最后统一报出失败的行
      failed.push(`${row.fundName ?? row.tradeNo ?? '未知行'}：${messageOf(error)}`)
    }
  }
  submitting.value = false
  // 成功的行从待确认里移走，失败的原样留着让人改
  orders.value = orders.value.filter((row) => row.seq == null || !doneSeqs.has(row.seq))
  selectedSeqs.value = selectedSeqs.value.filter((seq) => !doneSeqs.has(seq))
  if (failed.length) {
    submitNote.value = `成功 ${doneSeqs.size} 条，失败 ${failed.length} 条：${failed.join('；')}`
  } else {
    ElMessage.success(`已导入 ${doneSeqs.size} 条`)
  }
}

/**
 * 提交顺序：先按确认日升序，同一天内买入与转换在前、卖出在后
 *
 * <p>卖出要按 FIFO 扣已有批次，若先提交卖出，清仓基金的卖出会因为「当日尚无已建仓的批次」被拒。</p>
 *
 * @param rows 勾选的行
 * @returns 排序后的行
 */
function orderedForSubmit(rows: API.AlipayImportRow[]): API.AlipayImportRow[] {
  const rank = (action?: string): number => {
    // 转入侧必须先于卖出落地，否则同日卖出会因「无已建仓批次」被拒
    if (action === 'BUY' || action === 'CONVERT' || action === 'CONVERT_IN') {
      return 0
    }
    if (action === 'SELL') {
      return 1
    }
    return 2
  }
  return [...rows].sort((left, right) => {
    const leftDate = left.confirmDate ?? ''
    const rightDate = right.confirmDate ?? ''
    if (leftDate !== rightDate) {
      return leftDate.localeCompare(rightDate)
    }
    return rank(left.action) - rank(right.action)
  })
}

/**
 * 提交单行：买入走金额折算，卖出走金额反算，转换需两边份额
 *
 * @param row 待确认行
 * @returns 无
 */
async function submitRow(row: API.AlipayImportRow): Promise<void> {
  const varietyKey = row.varietyKey
  if (!varietyKey) {
    throw new Error('该行还没确定基金')
  }
  const confirmDate = row.confirmDate ?? undefined
  // 转换转入侧在账面上就是一笔按金额买入，与 BUY 同口径
  if (row.action === 'BUY' || row.action === 'CONVERT_IN') {
    await buyHolding({
      varietyKey,
      amount: row.amount ?? undefined,
      openDate: confirmDate,
      tradeNo: row.tradeNo ?? undefined,
    })
    return
  }
  if (row.action === 'SELL') {
    await sellHolding({
      varietyKey,
      amount: row.amount ?? undefined,
      tradeDate: confirmDate,
      tradeNo: row.tradeNo ?? undefined,
    })
    return
  }
  if (row.action === 'CONVERT') {
    // 只做卖出：转入侧由 #IN 那行按金额买入，走 convert 会让转入基金被买两遍
    await sellHolding({
      varietyKey,
      amount: transferOutOf(row),
      tradeDate: confirmDate,
      tradeNo: row.tradeNo ?? undefined,
      note: `转换转出至「${row.targetFundName ?? '转入基金'}」`,
    })
    return
  }
  // 分红只留档，不需要写持仓
  throw new Error(`${row.actionLabel ?? '该类'}行无需写入持仓`)
}

/**
 * 补录一笔红利再投：账单不记这类份额确认，只能对着支付宝的流水手工补一批
 *
 * @returns 无
 */
async function onReinvest(): Promise<void> {
  const shares = Number(reinvestForm.shares)
  if (!reinvestForm.varietyKey) {
    reinvestNote.value = '先选一只已注册的基金'
    return
  }
  if (!Number.isFinite(shares) || shares <= 0) {
    reinvestNote.value = '份额要填正数，按支付宝「份额确认」里的数字填'
    return
  }
  reinvesting.value = true
  reinvestNote.value = ''
  try {
    const date = reinvestForm.date || undefined
    await buyHolding({
      varietyKey: reinvestForm.varietyKey,
      shares,
      openDate: date,
      note: `红利再投，除息日 ${date ?? '未填'}，账单无记录，手工补录`,
    })
    ElMessage.success(`已补录 ${shares} 份红利再投`)
    reinvestForm.shares = ''
    reinvestForm.date = ''
  } catch (error) {
    reinvestNote.value = `补录失败：${messageOf(error)}`
  } finally {
    reinvesting.value = false
  }
}

/**
 * 取扩展名，无扩展名返回空串
 *
 * @param name 文件名
 * @returns 小写扩展名
 */
function extensionOf(name: string): string {
  const index = name.lastIndexOf('.')
  return index < 0 ? '' : name.slice(index + 1).toLowerCase()
}

/**
 * 从异常里取可读文案
 *
 * @param error 异常
 * @returns 文案
 */
function messageOf(error: unknown): string {
  return error instanceof Error && error.message ? error.message : '未知错误'
}

/**
 * 文件大小展示
 *
 * @param size 字节数
 * @returns 文本
 */
function sizeText(size?: number): string {
  if (size === undefined || size === null) {
    return '—'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

/**
 * 数值展示，缺值给破折号
 *
 * @param value 数值
 * @returns 文本
 */
function num(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

onMounted(() => {
  void load()
  void loadVarieties()
})
</script>

<style scoped>
/* 骨架类来自全局 styles/layout.css */
.app-importbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.app-importbar__note {
  font-size: 11px;
  color: var(--t4);
}

/* 筛选与补录行里的标签块，带上控件边框 */
.app-check {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--t3);
}

.app-select,
.app-date {
  min-width: 0;
  height: 28px;
  padding: 0 8px;
  border: 1px solid var(--hair);
  border-radius: 7px;
  background: var(--inp-bg);
  color: var(--t1);
  font-size: 12px;
  font-family: inherit;
}

.app-select {
  max-width: 220px;
}

.app-date {
  width: 138px;
}

.app-orders {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 520px;
  overflow-y: auto;
}

.app-order {
  display: flex;
  gap: 8px;
  padding: 9px 11px;
  border: 1px solid var(--hair);
  border-radius: 9px;
  background: var(--s1);
}

/* 未勾选的行压暗，一眼看出这次会提交哪些 */
.app-order--off {
  opacity: 0.55;
}

.app-order__check {
  flex: none;
  padding-top: 2px;
}

.app-order__main {
  flex: 1;
  min-width: 0;
}

.app-order__top {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  font-size: 12.5px;
}

.app-order__name {
  font-weight: 650;
  color: var(--t1);
}

.app-order__action {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--acc-soft);
  color: var(--acc);
  font-size: 10.5px;
}

.app-order__amount {
  font-variant-numeric: tabular-nums;
  color: var(--t2);
}

.app-order__status {
  margin-left: auto;
  font-size: 11px;
  color: var(--t3);
}

.app-order__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 3px;
  font-size: 11px;
  color: var(--t4);
}

.app-order__trade {
  font-variant-numeric: tabular-nums;
}

.app-order__reason {
  margin: 5px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--t3);
}

.app-order__pick {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--t3);
}

.app-upload {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.app-upload__input {
  display: none;
}

.app-upload__name {
  font-size: 12px;
  color: var(--t3);
}

.app-upload__error {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--up);
}

.app-shots {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.app-shots__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid var(--hair);
  border-radius: 8px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font-size: 12.5px;
  color: var(--t2);
}

.app-shots__item--on {
  border-color: var(--acc-line);
  background: var(--acc-soft);
}

.app-compare {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 14px;
}

@media (max-width: 900px) {
  .app-compare {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* 手机上表格比屏幕宽，横向滚动而不是撑破页面 */
@media (max-width: 720px) {
  .app-order {
    padding: 8px 9px;
  }

  .app-order__status {
    margin-left: 0;
  }

  .app-order__meta {
    gap: 4px 10px;
  }

  .app-importbar {
    gap: 8px;
  }

  .app-check {
    flex: 1 1 100%;
  }

  .app-select,
  .app-date {
    flex: 1;
    max-width: none;
    width: auto;
  }

  .app-orders {
    max-height: none;
  }

  .app-compare__right {
    overflow-x: auto;
  }

  .app-table {
    min-width: 420px;
  }
}

.app-compare__cap {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--t3);
}

.app-compare__img {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 220px;
  padding: 16px;
  border: 1px dashed var(--hair);
  border-radius: 10px;
  background: var(--group-bg);
  font-size: 12px;
  color: var(--t3);
  text-align: center;
}

.app-compare__img--link {
  padding: 6px;
  border-style: solid;
  background: var(--s1);
  cursor: zoom-in;
}

.app-compare__pic {
  display: block;
  width: 100%;
  max-height: 520px;
  border-radius: 6px;
  object-fit: contain;
}

.app-compare__tip {
  font-size: 11.5px;
  color: var(--t4);
}

.app-compare__notice {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.75;
  color: var(--t3);
}

.app-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}

.app-table th {
  padding: 6px;
  background: var(--group-bg);
  text-align: left;
  font-weight: 500;
  color: var(--t3);
}

.app-table td {
  padding: 6px;
  border-bottom: 1px solid var(--hair);
  color: var(--t2);
  vertical-align: top;
}

.app-table__num {
  text-align: right;
  white-space: nowrap;
}

.app-table__name {
  display: block;
  font-size: 11.5px;
  color: var(--t3);
}

.app-table__hint {
  margin-top: 2px;
  font-size: 11px;
  color: var(--t4);
}
</style>
