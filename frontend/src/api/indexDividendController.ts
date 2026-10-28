// @ts-ignore
/* eslint-disable */
import request from "@/request";

/** 查询股息率，条件为空时返回全部已知指数的最新一期 POST /api/dividend/list */
export async function list(
  body: API.DividendQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultListIndexDividendOverviewVO>("/api/dividend/list", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 手工录入股息率，国证系列与抓取失败时走这条路径 POST /api/dividend/manual */
export async function manual(
  body: API.DividendManualRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultIndexDividendOverviewVO>("/api/dividend/manual", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 刷新股息率，指定指数代码时只刷这几只，留空则按红利持仓的跟踪指数推导 POST /api/dividend/refresh */
export async function refresh(
  body: string[],
  options?: { [key: string]: any }
) {
  return request<API.ResultIndexDividendRefreshVO>("/api/dividend/refresh", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}
