package com.lilac.adapter.model;

/**
 * 交易场所身份，同一代码在沪市与深市可能各有一条
 *
 * @param code 6 位代码
 * @param name 名称回显
 * @param kind 身份类型
 */
public record ExchangeIdentity(String code, String name, ExchangeKind kind) {
}
