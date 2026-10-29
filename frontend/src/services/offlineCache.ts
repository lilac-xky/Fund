/**
 * 断网降级缓存：把最近一次成功取回的整页数据与补取时间落 localStorage，断网时原样回放
 */
import { ref } from 'vue'

/** 各页面的缓存键，改结构时同时改版本号，避免读到旧结构 */
export const CACHE_KEYS = {
  /** 总览页 */
  overview: 'fund-overview-cache-v1',
  /** 复盘页 */
  review: 'fund-review-cache-v1',
} as const

/** 一份缓存快照，data 是页面需要渲染的全部内容 */
export interface Snapshot<T> {
  /** 页面数据 */
  data: T
  /** 本次数据补取完成的时间，ISO 字符串 */
  fetchedAt: string
  /** 后端给出的数据日期，如行情所属交易日 */
  dataDate?: string
}

/** 当前是否处于断网状态，供页面置顶提示 */
export const offline = ref(typeof navigator === 'undefined' ? false : !navigator.onLine)

/**
 * 读取缓存，解析失败按无缓存处理
 *
 * @param key 缓存键，不同页面各用各的
 */
export function readSnapshot<T>(key: string): Snapshot<T> | null {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as Snapshot<T>
    // 结构不对的旧数据直接丢弃，页面回到「暂无缓存」而不是渲染出半个空壳
    return parsed && parsed.data && parsed.fetchedAt ? parsed : null
  } catch {
    return null
  }
}

/**
 * 写入缓存，写失败不影响页面主流程
 *
 * @param key 缓存键
 * @param snapshot 待写入的快照
 */
export function writeSnapshot<T>(key: string, snapshot: Snapshot<T>): void {
  try {
    localStorage.setItem(key, JSON.stringify(snapshot))
  } catch {
    // 隐私模式或配额满时静默降级：本次不缓存，页面仍可正常显示
  }
}

/** 监听浏览器上下线，返回取消监听的函数 */
export function watchOnline(onChange: (online: boolean) => void): () => void {
  const handleOnline = () => {
    offline.value = false
    onChange(true)
  }
  const handleOffline = () => {
    offline.value = true
    onChange(false)
  }
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  return () => {
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
  }
}

/** 时间戳格式化，页面统一用它显示数据截止时间 */
export function formatStamp(value?: string | null): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
