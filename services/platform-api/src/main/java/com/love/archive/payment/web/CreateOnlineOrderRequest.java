package com.love.archive.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param authorizationCode            公众号网页授权返回的 code（一次性）
 * @param authorizationDocumentVersion 用户付款前看到的授权书版本
 */
public record CreateOnlineOrderRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9_-]+") String authorizationCode,
        @NotBlank @Size(max = 32) String authorizationDocumentVersion) {
}
