/**
 * 极简全局消息提示：单例队列 + 固定右下角浮层，不依赖任何 UI 库
 */
import { reactive } from 'vue'

/** 消息类型 */
export type MessageKind = 'success' | 'error' | 'info'

/** 一条消息 */
export interface MessageItem {
  /** 唯一标识，用于按 key 移除 */
  id: number
  /** 类型 */
  kind: MessageKind
  /** 正文 */
  text: string
}

/** 全局消息队列，渲染层直接读它 */
export const messages = reactive<MessageItem[]>([])

let seq = 0

/**
 * 推入一条消息，3 秒后自动移除
 *
 * @param kind 类型
 * @param text 正文
 */
function push(kind: MessageKind, text: string): number {
  const id = ++seq
  messages.push({ id, kind, text })
  setTimeout(() => remove(id), 3200)
  return id
}

/**
 * 移除一条消息
 *
 * @param id 消息标识
 */
export function remove(id: number): void {
  const index = messages.findIndex((item) => item.id === id)
  if (index >= 0) {
    messages.splice(index, 1)
  }
}

/** 对外暴露的消息入口 */
export const ElMessage = {
  /**
   * 成功提示
   *
   * @param text 正文
   */
  success: (text: string) => push('success', text),
  /**
   * 错误提示
   *
   * @param text 正文
   */
  error: (text: string) => push('error', text),
  /**
   * 普通提示
   *
   * @param text 正文
   */
  info: (text: string) => push('info', text),
}
