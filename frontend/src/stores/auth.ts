/**
 * 登录状态：查会话、登录、登出，接口层走统一 Result，失败时页面就地显示原因
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import request from '@/request'

/** 登录状态返回体 */
export interface AuthStatus {
  /** 是否已登录 */
  authenticated?: boolean
  /** 登录开关是否开启 */
  required?: boolean
  /** 凭据是否已配置 */
  configured?: boolean
  /** 会话剩余秒数 */
  remainingSeconds?: number
}

export const useAuthStore = defineStore('auth', () => {
  /** 当前登录状态 */
  const status = ref<AuthStatus>({})

  /** 查询中 */
  const checking = ref(true)

  /** 登录中 */
  const submitting = ref(false)

  /** 是否已登录；登录开关关闭时一律视为可用 */
  const authenticated = computed(
    () => status.value.required === false || Boolean(status.value.authenticated),
  )

  /** 凭据是否已配置，未配置时页面直接提示去改密钥文件 */
  const configured = computed(
    () => status.value.required === false || Boolean(status.value.configured),
  )

  /** 查询登录状态，接口本身不要求登录 */
  async function refresh(): Promise<void> {
    checking.value = true
    try {
      const result = await request<{ code?: number; data?: AuthStatus }>(
        '/auth/status',
        { silent: true },
        '查询登录状态',
      )
      status.value = result.data ?? {}
    } catch {
      // 后端不可达时按未登录处理，页面会停在登录页，不会显示半截业务数据
      status.value = { authenticated: false, required: true, configured: false }
    } finally {
      checking.value = false
    }
  }

  /**
   * 登录，失败抛出 RequestError 由页面就地展示
   *
   * @param username 用户名
   * @param password 口令
   */
  async function login(username: string, password: string): Promise<void> {
    submitting.value = true
    try {
      const result = await request<{ code?: number; data?: AuthStatus }>(
        '/auth/login',
        { method: 'POST', data: { username, password }, silent: true },
        '登录',
      )
      status.value = result.data ?? { authenticated: true, required: true, configured: true }
    } finally {
      submitting.value = false
    }
  }

  /** 登出，无论后端是否成功都把本地状态置为未登录 */
  async function logout(): Promise<void> {
    try {
      await request('/auth/logout', { method: 'POST', silent: true }, '登出')
    } finally {
      status.value = { authenticated: false, required: true, configured: true }
    }
  }

  return { status, checking, submitting, authenticated, configured, refresh, login, logout }
})
