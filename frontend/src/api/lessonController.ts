// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 检查历史变动是否命中追高、盲目换仓或恐慌卖出 POST /api/lesson/list */
export async function list(body: API.LessonQueryRequest, options?: { [key: string]: any }) {
  return request<API.ResultLessonAdvisoryVO>('/api/lesson/list', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
