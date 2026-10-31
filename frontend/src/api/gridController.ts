// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 列出当前参数下的网格线 GET /api/grid/lines */
export async function lines(options?: { [key: string]: any }) {
  return request<API.ResultListBigDecimal>('/api/grid/lines', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 对全部持有中的场内 ETF 逐只判断是否触及网格线 POST /api/grid/list */
export async function list(options?: { [key: string]: any }) {
  return request<API.ResultGridAdvisoryVO>('/api/grid/list', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 读取通用网格参数与划格结果，未按标的分开配置时作为兜底 GET /api/grid/plan */
export async function plan(options?: { [key: string]: any }) {
  return request<API.ResultGridPlanVO>('/api/grid/plan', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 保存指定标的的网格参数，上下界颠倒或划不出两格时拒绝并指出字段 POST /api/grid/plan/save */
export async function savePlan(body: API.GridParamSaveRequest, options?: { [key: string]: any }) {
  return request<API.ResultGridPlanVO>('/api/grid/plan/save', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 读取指定标的的网格参数，页面逐只编辑时用它 GET /api/grid/plan/single */
export async function planOf(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.planOfParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultGridPlanVO>('/api/grid/plan/single', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 对单只标的试算，可手工指定当前价与参考价 POST /api/grid/single */
export async function single(body: API.GridAdviceRequest, options?: { [key: string]: any }) {
  return request<API.ResultGridAdviceVO>('/api/grid/single', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 列出网格的监控对象，说明每只 ETF 用的是哪套格子 GET /api/grid/watch */
export async function watch(options?: { [key: string]: any }) {
  return request<API.ResultListGridWatchVO>('/api/grid/watch', {
    method: 'GET',
    ...(options || {}),
  })
}
