// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 查询基金全集缓存状态 GET /api/fund/catalog/status */
export async function catalogStatus(options?: { [key: string]: any }) {
  return request<API.ResultFundCatalogStatusVO>('/api/fund/catalog/status', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 强制刷新基金全集缓存 POST /api/fund/catalog/sync */
export async function syncCatalog(options?: { [key: string]: any }) {
  return request<API.ResultFundCatalogSyncVO>('/api/fund/catalog/sync', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 确认候选并写入品种注册表 POST /api/fund/confirm */
export async function confirm(body: API.FundConfirmRequest, options?: { [key: string]: any }) {
  return request<API.ResultVarietyVO>('/api/fund/confirm', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 按 6 位代码返回候选列表与身份回显 POST /api/fund/disambiguate */
export async function disambiguate(
  body: API.FundDisambiguateRequest,
  options?: { [key: string]: any },
) {
  return request<API.ResultFundDisambiguateVO>('/api/fund/disambiguate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
