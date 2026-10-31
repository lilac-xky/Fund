// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 批量取当日行情，任一源失效自动切到备用源 POST /api/quote/batch */
export async function batch(body: API.QuoteBatchRequest, options?: { [key: string]: any }) {
  return request<API.ResultQuoteBatchVO>('/api/quote/batch', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 取港币兑人民币汇率 GET /api/quote/forex/hkd-cny */
export async function hkdCny(options?: { [key: string]: any }) {
  return request<API.ResultForexRateVO>('/api/quote/forex/hkd-cny', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 取多只标的的前复权日线 GET /api/quote/history */
export async function history(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.historyParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultListMarketHistoryVO>('/api/quote/history', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
