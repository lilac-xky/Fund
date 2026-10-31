// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 对全部持有中的红利基金逐只给加减仓建议 POST /api/dividend/advice/list */
export async function list(options?: { [key: string]: any }) {
  return request<API.ResultDividendAdvisoryVO>('/api/dividend/advice/list', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 读取红利参数，含不合法组合的问题清单 GET /api/dividend/advice/plan */
export async function plan(options?: { [key: string]: any }) {
  return request<API.ResultDividendPlanVO>('/api/dividend/advice/plan', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 对单只红利基金给建议，可手工指定当前金额用于核对规则 POST /api/dividend/advice/single */
export async function single(body: API.DividendAdviceRequest, options?: { [key: string]: any }) {
  return request<API.ResultDividendAdviceVO>('/api/dividend/advice/single', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 列出红利策略的监控对象，说明这套参数管的是哪几只基金 GET /api/dividend/advice/watch */
export async function watch(options?: { [key: string]: any }) {
  return request<API.ResultListDividendWatchVO>('/api/dividend/advice/watch', {
    method: 'GET',
    ...(options || {}),
  })
}
