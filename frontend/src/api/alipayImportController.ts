// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 上传支付宝账单明细并给出待确认清单，解析失败明确指出原因 POST /api/alipay/preview */
export async function preview(body: {}, file?: File, options?: { [key: string]: any }) {
  const formData = new FormData()

  if (file) {
    formData.append('file', file)
  }

  Object.keys(body).forEach((ele) => {
    const item = (body as any)[ele]

    if (item !== undefined && item !== null) {
      if (typeof item === 'object' && !(item instanceof File)) {
        if (item instanceof Array) {
          item.forEach((f) => formData.append(ele, f || ''))
        } else {
          formData.append(ele, new Blob([JSON.stringify(item)], { type: 'application/json' }))
        }
      } else {
        formData.append(ele, item)
      }
    }
  })

  return request<API.ResultListAlipayImportRow>('/api/alipay/preview', {
    method: 'POST',
    data: formData,
    requestType: 'form',
    ...(options || {}),
  })
}
