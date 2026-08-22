package com.love.archive.identity.application;

/** 停用 / 启用后的账号状态。 */
public record AccountStatusView(long accountId, String status) {
}
