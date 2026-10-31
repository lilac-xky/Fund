// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 统一查询策略参数，各条件为空时不过滤 POST /api/param/list */
export async function list(body: API.ParamQueryRequest, options?: { [key: string]: any }) {
  return request<API.ResultListStrategyParamVO>('/api/param/list', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 按「分组 + 参数名」新增或更新参数 POST /api/param/save */
export async function save(body: API.ParamSaveRequest, options?: { [key: string]: any }) {
  return request<API.ResultBoolean>('/api/param/save', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
