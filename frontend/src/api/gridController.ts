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

/** 读取网格参数与划格结果 GET /api/grid/plan */
export async function plan(options?: { [key: string]: any }) {
  return request<API.ResultGridPlanVO>('/api/grid/plan', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 保存网格参数，上下界颠倒或划不出两格时拒绝并指出字段 POST /api/grid/plan/save */
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
