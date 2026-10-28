// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 按 6 位代码取净值、持仓与三个日期，上游失败时回退最近一次成功缓存 GET /api/fund/snapshot */
export async function snapshot(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.snapshotParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultFundSnapshotVO>('/api/fund/snapshot', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
