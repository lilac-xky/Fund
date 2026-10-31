// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 对全部持有中的标的算指标并汇总信号 POST /api/indicator/list */
export async function list(options?: { [key: string]: any }) {
  return request<API.ResultIndicatorAdvisoryVO>('/api/indicator/list', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 按品种业务主键算指标，页面切换标的时用它 POST /api/indicator/one */
export async function one(body: API.IndicatorQueryRequest, options?: { [key: string]: any }) {
  return request<API.ResultIndicatorVO>('/api/indicator/one', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 对指定代码算指标，核对与行情软件的偏差时用它 GET /api/indicator/single */
export async function single(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.singleParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultIndicatorVO>('/api/indicator/single', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
