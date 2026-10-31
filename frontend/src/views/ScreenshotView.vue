<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <AppTopBar active="screenshot">
      <template #status>
        {{ loading ? '读取中…' : `${shots.length} 张截图` }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimable-count="check?.rows?.length ?? 0"
      :total-count="check?.rows?.length ?? 0"
      :market-mood="current ? '待人工核对' : '未选择截图'"
      :next-note="current ? (current.fileName ?? '') : ''"
    />

    <div class="app-body">
      <div class="app-stage app-stage--no-aside">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                持仓 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">截核对</b>
              </div>
              <div class="app-eyebrow">Screenshot</div>
              <h1 class="app-h1">持仓截图核对</h1>
              <p class="app-lede">
                传一张支付宝或券商的持仓截图，与系统里录入的数字左右对照，确认没录错。
                图片只做留存，不做自动识别——工具没有识别能力，不猜数字。
              </p>
            </div>

            <AppAlert kind="info">
              本页只保证右侧数字来自当前库内记录。<strong>差异需要你自己用眼睛比对</strong>，
              工具不会替你判断哪只录错了。
            </AppAlert>

            <AppGroup title="上传" code="UPLOAD" meta="png / jpg / webp，≤ 10MB">
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
 * 持仓截图核对页：留存截图并与已录入持仓并排展示，由使用者人工比对
 */
import { computed, onMounted, ref } from 'vue'
import AppAlert from '@/ui/AppAlert.vue'
import AppButton from '@/ui/AppButton.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import {
  check as fetchCheck,
  list as fetchList,
  upload as uploadShot,
} from '@/api/screenshotController'
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

/** 核对请求序号，连续点选时只认最后一次的响应 */
let checkSeq = 0

/** 图片直链，img 标签直接取字节；带 Cookie 的同源请求，不经过 JSON 封装的 request */
const rawUrl = computed(() => {
  const fileName = current.value?.fileName
  return fileName ? `${CONTEXT_PATH}/screenshot/raw?fileName=${encodeURIComponent(fileName)}` : ''
})

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
})
</script>

<style scoped>
/* 骨架类来自全局 styles/layout.css */
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
