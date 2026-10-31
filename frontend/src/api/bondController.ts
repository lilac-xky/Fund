// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 对全部持有中的债券基金判定横盘与趋势转弱 POST /api/bond/list */
export async function list(options?: { [key: string]: any }) {
  return request<API.ResultBondAdvisoryVO>('/api/bond/list', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 读取债券监控参数，含问题清单 GET /api/bond/plan */
export async function plan(options?: { [key: string]: any }) {
  return request<API.ResultBondPlanVO>('/api/bond/plan', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 保存债券监控参数，横盘天数超过回看窗口时拒绝并指出字段 POST /api/bond/plan/save */
export async function savePlan(body: API.BondParamSaveRequest, options?: { [key: string]: any }) {
  return request<API.ResultBondPlanVO>('/api/bond/plan/save', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 对单只债券基金试算，核对横盘天数与趋势指标 POST /api/bond/single */
export async function single(body: API.BondMonitorRequest, options?: { [key: string]: any }) {
  return request<API.ResultBondMonitorVO>('/api/bond/single', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
