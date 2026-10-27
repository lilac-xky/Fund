// @ts-ignore
/* eslint-disable */
import request from "@/request";

/** 按主键删除参数 POST /api/param/delete */
export async function deleteUsingPost(
  body: API.DeleteRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultBoolean>("/api/param/delete", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 按主键查询单个参数 GET /api/param/get */
export async function getById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getByIdParams,
  options?: { [key: string]: any }
) {
  return request<API.ResultStrategyParamVO>("/api/param/get", {
    method: "GET",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** 统一查询策略参数，各条件为空时不过滤 POST /api/param/list */
export async function list(
  body: API.ParamQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultListStrategyParamVO>("/api/param/list", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 按「分组 + 参数名」新增或更新参数 POST /api/param/save */
export async function save(
  body: API.ParamSaveRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultBoolean>("/api/param/save", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}
