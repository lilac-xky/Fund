/**
 * 统一请求封装：兼容生成层的 umi 风格（data / params），统一解析 Result，失败抛出带环节与时间的错误
 */
import { ElMessage } from './ui/message'

/** 应用上下文路径，与后端 server.servlet.context-path 保持一致 */
const CONTEXT_PATH = '/api'

/** 请求超时，毫秒；外部数据源偶发卡住时不能把页面拖死 */
const TIMEOUT_MS = 20000

/** 后端未登录的业务码，与 HttpsCodeEnum.NEED_LOGIN 一致 */
const NEED_LOGIN_CODE = 400001

/** 登录页路径 */
const LOGIN_PATH = '/login'

/**
 * 会话失效时跳登录页，带上当前地址以便登录后回跳
 */
function redirectToLogin(): void {
  if (typeof window === 'undefined') {
    return
  }
  const current = window.location.pathname + window.location.search
  if (current.startsWith(LOGIN_PATH)) {
    return
  }
  window.location.href = `${LOGIN_PATH}?redirect=${encodeURIComponent(current)}`
}

/** 后端统一返回体 */
export interface ApiResult<T> {
  code?: number
  msg?: string
  data?: T
}

/** 兼容生成层的请求配置：data 作为请求体，params 作为查询串 */
export interface RequestOptions extends Omit<RequestInit, 'body'> {
  /** 请求体，对象会被序列化为 JSON */
  data?: unknown
  /** 查询参数，值为空时自动跳过 */
  params?: Record<string, unknown>
  /** 静默模式，失败时不弹右下角提示，由调用方自行展示 */
  silent?: boolean
  /** 生成层会带 umi 风格的 requestType，本封装按 FormData 自带边界处理，此处仅做兼容声明 */
  requestType?: 'json' | 'form'
}

/** 请求错误，带上出错环节与发生时间，页面据此显示「哪里出错、什么时候」 */
export class RequestError extends Error {
  /** 出错环节，如「保存参数」 */
  readonly stage: string
  /** 发生时间，本地时间字符串 */
  readonly happenedAt: string
  /** 后端返回的业务码 */
  readonly code?: number

  constructor(message: string, stage: string, code?: number) {
    super(message)
    this.name = 'RequestError'
    this.stage = stage
    this.happenedAt = formatNow()
    this.code = code
  }
}

/** 当前时间，格式化为 HH:mm:ss 便于在页面展示 */
function formatNow(): string {
  const now = new Date()
  return [now.getHours(), now.getMinutes(), now.getSeconds()]
    .map((value) => String(value).padStart(2, '0'))
    .join(':')
}

/** 拼出完整请求地址，已带 /api 前缀的不重复拼接 */
function resolveUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) {
    return url
  }
  const path = url.startsWith('/') ? url : `/${url}`
  return path.startsWith(CONTEXT_PATH) ? path : `${CONTEXT_PATH}${path}`
}

/** 从返回体中取出可读的错误文案 */
function messageOf(result: ApiResult<unknown>, stage: string): string {
  if (result?.msg && result.msg.trim().length > 0) {
    return result.msg
  }
  return `${stage}失败（业务码 ${result?.code ?? '未知'}）`
}

/**
 * 发起请求并校验 Result，成功返回整个返回体，失败抛出 RequestError
 *
 * @param url 接口地址，可省略 /api 前缀
 * @param options 请求配置，data 作为请求体、params 作为查询串
 * @param stage 出错时展示的环节名，如「保存参数」
 */
export default async function request<T>(
  url: string,
  options: RequestOptions = {},
  stage = '请求',
): Promise<T> {
  const { data, params, silent, requestType, ...rest } = options
  // requestType 是生成层的 umi 风格标记；上传走 FormData 时 body 已是 FormData，不做 JSON 序列化
  const isFormData = typeof FormData !== 'undefined' && data instanceof FormData
  const query = new URLSearchParams()
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') {
        return
      }
      // 数组按重复参数逐个追加，直接 String(array) 会得到一个逗号拼接的串
      if (Array.isArray(value)) {
        value.forEach((item) => {
          if (item !== undefined && item !== null && item !== '') {
            query.append(key, String(item))
          }
        })
        return
      }
      query.append(key, String(value))
    })
  }
  const queryString = query.toString()
  const target = resolveUrl(url) + (queryString ? `?${queryString}` : '')

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  try {
    const response = await fetch(target, {
      ...rest,
      signal: controller.signal,
      headers: {
        Accept: 'application/json',
        // FormData 自带 multipart 边界，手工设置 Content-Type 会破坏边界
        ...(data !== undefined && !isFormData ? { 'Content-Type': 'application/json' } : {}),
        ...(rest.headers ?? {}),
      },
      body: data === undefined ? undefined : isFormData ? (data as FormData) : JSON.stringify(data),
    })
    if (!response.ok) {
      // 业务异常也走统一 Result 结构，非 2xx 时尽量读出 body 里的 msg，不丢后端原因
      let businessMessage = ''
      let businessCode: number | undefined
      try {
        const body = (await response.json()) as ApiResult<unknown>
        businessMessage = body?.msg?.trim() ?? ''
        businessCode = body?.code
      } catch {
        // body 不是 JSON 时按纯状态码处理
      }
      // 401 或 NEED_LOGIN 一律视为会话失效，跳登录页并带回跳地址
      if (response.status === 401 || businessCode === NEED_LOGIN_CODE) {
        redirectToLogin()
      }
      const detail = businessMessage || `服务端返回 ${response.status}`
      const error = new RequestError(detail, stage, businessCode ?? response.status)
      if (!silent) {
        ElMessage.error(`${error.stage}失败：${error.message}（${error.happenedAt}）`)
      }
      throw error
    }
    const result = (await response.json()) as T
    // 业务码 200 为成功，其余一律按失败处理
    const code = (result as unknown as ApiResult<unknown>)?.code
    if (code === NEED_LOGIN_CODE) {
      redirectToLogin()
    }
    if (code !== 200) {
      const error = new RequestError(
        messageOf(result as unknown as ApiResult<unknown>, stage),
        stage,
        code,
      )
      if (!silent) {
        ElMessage.error(`${error.stage}失败：${error.message}（${error.happenedAt}）`)
      }
      throw error
    }
    return result
  } catch (error) {
    if (error instanceof RequestError) {
      throw error
    }
    const aborted = error instanceof DOMException && error.name === 'AbortError'
    const fallback = aborted ? '请求超时' : '网络不可达'
    const wrapped = new RequestError(fallback, stage)
    if (!silent) {
      ElMessage.error(`${stage}失败：${fallback}（${wrapped.happenedAt}）`)
    }
    throw wrapped
  } finally {
    clearTimeout(timer)
  }
}
