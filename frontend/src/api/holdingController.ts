// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 撤销某个持仓批次，已卖出时拒绝并提示改为更正 POST /api/holding/batch/revoke */
export async function revokeBatch(
  body: API.HoldingChangeRevokeRequest,
  options?: { [key: string]: any },
) {
  return request<API.ResultBoolean>('/api/holding/batch/revoke', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 更正某个持仓批次的份额、成本价与建仓日期，供录入出错时改正 POST /api/holding/batch/update */
export async function updateBatch(
  body: API.HoldingBatchUpdateRequest,
  options?: { [key: string]: any },
) {
  return request<API.ResultHoldingVO>('/api/holding/batch/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 买入入库，校验失败拒绝并指出原因 POST /api/holding/buy */
export async function buy(body: API.HoldingBuyRequest, options?: { [key: string]: any }) {
  return request<API.ResultHoldingVO>('/api/holding/buy', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 查询持仓变动历史，按时间倒序 POST /api/holding/changes */
export async function changes(
  body: API.HoldingChangeQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.ResultListHoldingChangeVO>('/api/holding/changes', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 基金转换，同一事务内先卖出再买入，任一步失败整笔回滚 POST /api/holding/convert */
export async function convert(body: API.HoldingConvertRequest, options?: { [key: string]: any }) {
  return request<API.ResultHoldingConvertVO>('/api/holding/convert', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 统一查询持仓，各条件为空时不过滤 POST /api/holding/list */
export async function list(body: API.HoldingQueryRequest, options?: { [key: string]: any }) {
  return request<API.ResultListHoldingVO>('/api/holding/list', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 持仓总览，按品种聚合并带出名称与最新净值 GET /api/holding/overview */
export async function overview(options?: { [key: string]: any }) {
  return request<API.ResultHoldingOverviewVO>('/api/holding/overview', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 卖出扣减，按先进先出扣已有持仓 POST /api/holding/sell */
export async function sell(body: API.HoldingSellRequest, options?: { [key: string]: any }) {
  return request<API.ResultListHoldingVO>('/api/holding/sell', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 按品种汇总持有中份额与加权成本，供消歧候选回显 GET /api/holding/summary */
export async function summary(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.summaryParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultHoldingSummaryVO>('/api/holding/summary', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
