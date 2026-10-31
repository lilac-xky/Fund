// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 批量指定类型与跟踪指数，指定后预估与策略立即按新类型生效 POST /api/variety/category/assign */
export async function assignCategory(
  body: API.CategoryBatchRequest,
  options?: { [key: string]: any },
) {
  return request<API.ResultInteger>('/api/variety/category/assign', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
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

/** 列出待确认类型的标的，带推断依据与建议类型 GET /api/variety/pending */
export async function pending(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.pendingParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultPendingCategoryVO>('/api/variety/pending', {
    method: 'GET',
    params: {
      ...params,
    },
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
