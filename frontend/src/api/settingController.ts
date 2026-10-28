// @ts-ignore
/* eslint-disable */
import request from "@/request";

/** 读取邮件设置，授权码只回「已配置 / 未配置」 GET /api/setting/mail */
export async function mailSetting(options?: { [key: string]: any }) {
  return request<API.ResultMailSettingVO>("/api/setting/mail", {
    method: "GET",
    ...(options || {}),
  });
}

/** 保存邮件设置，授权码写入密钥文件且永不回显 POST /api/setting/mail/save */
export async function saveMailSetting(
  body: API.MailSettingSaveRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultSettingSaveVO>("/api/setting/mail/save", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 读取参数设置页的全部数据，含四个分组、邮件状态与生效说明 GET /api/setting/overview */
export async function overview(options?: { [key: string]: any }) {
  return request<API.ResultSettingOverviewVO>("/api/setting/overview", {
    method: "GET",
    ...(options || {}),
  });
}

/** 把指定分组的参数恢复为内置默认值 POST /api/setting/reset */
export async function reset(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.resetParams,
  options?: { [key: string]: any }
) {
  return request<API.ResultSettingSaveVO>("/api/setting/reset", {
    method: "POST",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** 批量保存参数，不合法时返回逐字段的原因且整批不落库 POST /api/setting/save */
export async function save(
  body: API.SettingBatchSaveRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultSettingSaveVO>("/api/setting/save", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}
