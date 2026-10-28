// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 立即扫描当日应发未发的时点并补跑，等价于重启后的补跑动作 POST /api/task/catch-up */
export async function catchUp(options?: { [key: string]: any }) {
  return request<API.ResultInteger>('/api/task/catch-up', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 查询当日四个时点的发送状态 GET /api/task/overview */
export async function overview(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.overviewParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultTaskOverviewVO>('/api/task/overview', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 查询发送留痕，条件为空时不过滤 POST /api/task/records */
export async function records(body: API.SendRecordQueryRequest, options?: { [key: string]: any }) {
  return request<API.ResultListSendRecordVO>('/api/task/records', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 手动触发某个时点，供补发与调试使用 POST /api/task/trigger */
export async function trigger(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.triggerParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultPointOutcome>('/api/task/trigger', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
