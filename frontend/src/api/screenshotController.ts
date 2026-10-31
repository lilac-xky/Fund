// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 取指定截图与当前持仓的核对视图 GET /api/screenshot/check */
export async function check(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.checkParams,
  options?: { [key: string]: any },
) {
  return request<API.ResultScreenshotCheckVO>('/api/screenshot/check', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 列出已留存的截图，按上传时间倒序 GET /api/screenshot/list */
export async function list(options?: { [key: string]: any }) {
  return request<API.ResultListScreenshotVO>('/api/screenshot/list', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 直接返回图片字节，供页面 img 标签显示；二进制流无法包进 Result，属规范里唯一的例外 GET /api/screenshot/raw */
export async function raw(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.rawParams,
  options?: { [key: string]: any },
) {
  return request<number[]>('/api/screenshot/raw', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 上传一张持仓截图，支持 png / jpg / jpeg / webp，上限 10MB POST /api/screenshot/upload */
export async function upload(body: {}, file?: File, options?: { [key: string]: any }) {
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

  return request<API.ResultScreenshotVO>('/api/screenshot/upload', {
    method: 'POST',
    data: formData,
    requestType: 'form',
    ...(options || {}),
  })
}
