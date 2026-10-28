// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 按主键删除品种 POST /api/variety/delete */
export async function deleteUsingPost(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.ResultBoolean>('/api/variety/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 按主键查询单个品种 GET /api/variety/get */
export async function getById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getByIdParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultVarietyVO>('/api/variety/get', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

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
