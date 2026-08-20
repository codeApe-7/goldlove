package com.love.archive.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param authorizationCode            需要前置授权的渠道（微信）传入网页授权 code；
 *                                    无需授权的渠道（易支付）传空
 * @param authorizationDocumentVersion 用户付款前看到的授权书版本
 */
public record CreateOnlineOrderRequest(
        @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9_-]*") String authorizationCode,
        @NotBlank @Size(max = 32) String authorizationDocumentVersion) {
}
