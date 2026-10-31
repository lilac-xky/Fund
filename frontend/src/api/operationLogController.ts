// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 按日期区间导出操作日志 CSV，Excel 打开中文不乱码 GET /api/oplog/export */
export async function exportUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.exportUsingGETParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultString>('/api/oplog/export', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 查询统一操作日志，按发生时间倒序 POST /api/oplog/list */
export async function list(body: API.OperationLogQueryRequest, options?: { [key: string]: any }) {
  return request<API.ResultListOperationLogVO>('/api/oplog/list', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
