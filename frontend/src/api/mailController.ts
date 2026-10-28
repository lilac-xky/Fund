// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 预览邮件正文，便于在发信前确认排版与内容 GET /api/mail/preview */
export async function preview(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.previewParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultMailPreviewVO>('/api/mail/preview', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 手动补发收盘前提醒，用于改好 SMTP 口令后把未送达内容补出去 POST /api/mail/resend */
export async function resend(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.resendParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultString>('/api/mail/resend', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 查询邮件通道是否就绪，只回「已配置 / 未配置」，不回显授权码 GET /api/mail/status */
export async function status(options?: { [key: string]: any }) {
  return request<API.ResultMailStatusVO>('/api/mail/status', {
    method: 'GET',
    ...(options || {}),
  })
}
