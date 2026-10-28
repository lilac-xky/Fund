// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 按持仓批量预估当日涨跌，不可预估的单独成组 POST /api/estimate/batch */
export async function batch(body: API.EstimateBatchRequest, options?: { [key: string]: any }) {
  return request<API.ResultEstimateBatchVO>('/api/estimate/batch', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 预估单只基金 POST /api/estimate/single */
export async function single(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.singleParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultEstimateVO>('/api/estimate/single', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
