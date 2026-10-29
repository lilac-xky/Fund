// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 登录，连续失败达到阈值后临时锁定 POST /api/auth/login */
export async function login(body: API.LoginRequest, options?: { [key: string]: any }) {
  return request<API.ResultAuthStatusVO>('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 登出，作废会话并清掉 Cookie POST /api/auth/logout */
export async function logout(options?: { [key: string]: any }) {
  return request<API.ResultVoid>('/api/auth/logout', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 查询登录状态，未登录时也返回 200，由前端据 authenticated 决定是否跳转 GET /api/auth/status */
export async function status(options?: { [key: string]: any }) {
  return request<API.ResultAuthStatusVO>('/api/auth/status', {
    method: 'GET',
    ...(options || {}),
  })
}
