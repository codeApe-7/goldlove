package com.love.archive.wechatpay.application;

public record CreateOrderResult(String prepayId, JsapiPayParameters payParameters) {
}
