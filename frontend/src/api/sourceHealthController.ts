// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 取各数据源健康度与降级矩阵，失败率偏高时给出改版提示 POST /api/source/health */
export async function health(body: API.SourceEventQueryRequest, options?: { [key: string]: any }) {
  return request<API.ResultSourceHealthVO>('/api/source/health', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
