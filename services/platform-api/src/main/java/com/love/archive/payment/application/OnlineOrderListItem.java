package com.love.archive.payment.application;

import com.love.archive.payment.domain.PaymentOrderStatus;
import java.time.OffsetDateTime;

/**
 * 「我的订单」列表项。
 *
 * <p>存在的理由是：订单号原本只活在前端的会话存储里，换个标签页或重新登录就丢，
 * 而查单接口要求调用方**已经知道订单号**——两下一凑，一笔没付成的订单就永久失联了。
 * 唯一能把它找回来的地方是服务端。</p>
 *
 * <p>{@code status} 为 {@code CREATED} 即「还能继续付」：列表在返回前会把该账号下
 * 已过期的 CREATED 订单一并关掉，所以这里不需要再给前端一个 payable 标记。</p>
 */
public record OnlineOrderListItem(
        String outTradeNo,
        PaymentOrderStatus status,
        long amountMinor,
        String channelTradeNo,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt,
        OffsetDateTime expiresAt) {
}
