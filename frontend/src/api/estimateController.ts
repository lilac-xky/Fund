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

/** 读取整体预估的盘中轨迹，行情条据此画出当天走向 GET /api/estimate/track */
export async function track(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.trackParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultEstimateTrackVO>('/api/estimate/track', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
