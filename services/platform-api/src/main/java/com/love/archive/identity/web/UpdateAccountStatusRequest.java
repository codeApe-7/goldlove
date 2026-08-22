package com.love.archive.identity.web;

import jakarta.validation.constraints.Size;

/**
 * 停用 / 启用账号的请求体。
 *
 * @param reason 选填的原因备注（规范图 7.2 的「可选备注」）。没有专门的表存它，
 *               写进 audit_log.metadata——那本来就是记录「谁在什么时候做了什么」的地方。
 */
public record UpdateAccountStatusRequest(@Size(max = 200) String reason) {
}
