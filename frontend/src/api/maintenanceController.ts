// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 执行清理，同组只留最早一条 POST /api/maintenance/duplicate/clean */
export async function clean(options?: { [key: string]: any }) {
  return request<API.ResultString>('/api/maintenance/duplicate/clean', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 预览重复批次，不写库 GET /api/maintenance/duplicate/preview */
export async function preview(options?: { [key: string]: any }) {
  return request<API.ResultListDuplicateCleanVO>('/api/maintenance/duplicate/preview', {
    method: 'GET',
    ...(options || {}),
  })
}
