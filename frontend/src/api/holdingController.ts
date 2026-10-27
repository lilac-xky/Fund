// @ts-ignore
/* eslint-disable */
import request from "@/request";

/** 新增持仓 POST /api/holding/add */
export async function add(
  body: API.HoldingAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultBoolean>("/api/holding/add", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 按主键逻辑删除持仓 POST /api/holding/delete */
export async function deleteUsingPost(
  body: API.DeleteRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultBoolean>("/api/holding/delete", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 按主键查询单个持仓 GET /api/holding/get */
export async function getById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getByIdParams,
  options?: { [key: string]: any }
) {
  return request<API.ResultHoldingVO>("/api/holding/get", {
    method: "GET",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** 统一查询持仓，各条件为空时不过滤 POST /api/holding/list */
export async function list(
  body: API.HoldingQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultListHoldingVO>("/api/holding/list", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 按主键更新持仓 POST /api/holding/update */
export async function update(
  body: API.HoldingUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultBoolean>("/api/holding/update", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}
