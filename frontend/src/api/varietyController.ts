// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 统一查询品种，各条件为空时不过滤 POST /api/variety/list */
export async function list(body: API.VarietyQueryRequest, options?: { [key: string]: any }) {
  return request<API.ResultListVarietyVO>('/api/variety/list', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 新增或按业务主键更新品种 POST /api/variety/save */
export async function save(body: API.VarietySaveRequest, options?: { [key: string]: any }) {
  return request<API.ResultBoolean>('/api/variety/save', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
