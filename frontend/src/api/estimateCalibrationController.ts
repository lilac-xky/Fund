// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 查询误差历史，条件为空时不过滤 POST /api/estimate/calibration/errors */
export async function errors(
  body: API.EstimateErrorQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.ResultListEstimateErrorVO>('/api/estimate/calibration/errors', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 查询黄金 β 记录，条件为空时不过滤 GET /api/estimate/calibration/gold-beta */
export async function goldBeta(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.goldBetaParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultListGoldBetaRecordVO>('/api/estimate/calibration/gold-beta', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 执行某一交易日的晚间校准，重复执行同一天只覆盖不重复累计 POST /api/estimate/calibration/run */
export async function run(body: API.CalibrationRequest, options?: { [key: string]: any }) {
  return request<API.ResultCalibrationResultVO>('/api/estimate/calibration/run', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 列出建议停用预估的基金，连续多期超阈值才会出现 GET /api/estimate/calibration/suspend-hints */
export async function suspendHints(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.suspendHintsParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultListEstimateErrorTrendVO>('/api/estimate/calibration/suspend-hints', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 查询单只基金的误差趋势 GET /api/estimate/calibration/trend */
export async function trend(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.trendParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultEstimateErrorTrendVO>('/api/estimate/calibration/trend', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
