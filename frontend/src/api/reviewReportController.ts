// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 导出预估与实际对照的 CSV，Excel 打开中文不乱码 GET /api/report/export/compare */
export async function exportCompare(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.exportCompareParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultReviewReportVO>('/api/report/export/compare', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 按日期区间导出误差历史 CSV GET /api/report/export/errors */
export async function exportErrors(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.exportErrorsParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultString>('/api/report/export/errors', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 生成当日复盘报告并留存在 data/report 下 POST /api/report/generate */
export async function generate(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.generateParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultReviewReportVO>('/api/report/generate', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
