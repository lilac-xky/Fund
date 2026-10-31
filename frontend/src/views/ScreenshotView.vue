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
      :next-note="current ? current.fileName : ''"
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
              <p class="app-hint">
                上传接口（`POST /screenshot/upload`）尚未生成到 `src/api/`，
                重新生成后这里可以直接选文件上传。
              </p>
            </AppGroup>

            <AppGroup title="已留存" code="LIST" :meta="`${shots.length} 张`">
              <template #icon>
                <span v-html="ICONS.gallery" />
              </template>

              <div v-if="!shots.length" class="app-listempty">
                还没有截图。上传接口生成后，传一张就能在这里对照。
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
                  <span>{{ sizeText(shot.size) }} · {{ shot.uploadedAt }}</span>
                </button>
              </div>
            </AppGroup>

            <AppGroup
              v-if="current"
              title="并排核对"
              code="CHECK"
              :meta="`${check?.rows?.length ?? 0} 只待比对`"
              :meta-ok="false"
            >
              <template #icon>
                <span v-html="ICONS.compare" />
              </template>

              <div class="app-compare">
                <div class="app-compare__left">
                  <p class="app-compare__cap">截图</p>
                  <div class="app-compare__img">
                    <span>{{ current.fileName }}</span>
                    <span class="app-compare__tip">
                      取图接口（`GET /screenshot/raw`）生成后此处直接显示图片
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
import { onMounted, ref } from 'vue'
import AppAlert from '@/ui/AppAlert.vue'
import AppButton from '@/ui/AppButton.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppTopBar from '@/layout/AppTopBar.vue'

/** 内联图标 */
const ICONS: Record<string, string> = {
  upload:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V4M8 8l4-4 4 4"/><path d="M4 15v3.5A1.5 1.5 0 0 0 5.5 20h13a1.5 1.5 0 0 0 1.5-1.5V15"/></svg>',
  gallery:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8.5" cy="9.5" r="1.6"/><path d="m4 17 5-5 4.5 4.5L17 13l3 3"/></svg>',
  compare:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="2.5" y="5" width="8" height="14" rx="1.6"/><rect x="13.5" y="5" width="8" height="14" rx="1.6"/></svg>',
}

const loading = ref(false)
const uploading = ref(false)
const pickedName = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const shots = ref<Shot[]>([])
const current = ref<Shot | null>(null)
const check = ref<Check | null>(null)

/**
 * 本地类型：接口生成后应换成 API.ScreenshotVO。
 * 现在生成层还没有这两个类型，先在页面里定义，字段与后端 VO 一一对应。
 */
interface Shot {
  fileName?: string
  originalName?: string
  size?: number
  uploadedAt?: string
  path?: string
}

/** 本地类型：接口生成后应换成 API.ScreenshotCheckVO */
interface Check {
  screenshot?: Shot
  rows?: CheckRow[]
  diffCount?: number
  notice?: string
}

/** 核对行：接口生成后应换成 API 里对应的内部类 */
interface CheckRow {
  varietyKey?: string
  varietyCode?: string
  varietyName?: string
  shares?: number
  avgCostPrice?: number
  marketValue?: number
  status?: string
  hints?: string[]
}

/**
 * 读取已留存列表
 *
 * @returns 无
 */
async function load(): Promise<void> {
  loading.value = true
  try {
    // 接口生成后换成 screenshot/list，当前先留空避免调用不存在的模块
    shots.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 选中一张截图
 *
 * @param shot 截图
 * @returns 无
 */
function select(shot: Shot): void {
  current.value = shot
  check.value = null
}

/**
 * 选中文件后预备上传
 *
 * @param event 选择事件
 * @returns 无
 */
function onPick(event: Event): void {
  const input = event.target as HTMLInputElement
  pickedName.value = input.files?.[0]?.name ?? ''
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

onMounted(load)
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
