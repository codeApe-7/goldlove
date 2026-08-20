package com.love.archive.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param authorizationCode            需要前置授权的渠道（微信）传入网页授权 code；
 *                                    无需授权的渠道（易支付）传空
 * @param authorizationDocumentVersion 用户付款前看到的授权书版本
 * @param phone                        注册用手机号。下单前预检是否已有账号，并钉在订单上供
 *                                    注册时校验一致，避免出现「已付款但无法注册」的卡死订单
 */
public record CreateOnlineOrderRequest(
        @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9_-]*") String authorizationCode,
        @NotBlank @Size(max = 32) String authorizationDocumentVersion,
        @NotBlank @Size(max = 32) String phone) {
}
