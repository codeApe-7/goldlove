package com.love.archive.payment.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * 商户订单号，形如 {@code GOLD-20260824-103512-K7Q3F9}。
 *
 * <p>原来是 24 字节随机数的 Base64URL（{@code -2EbDnKRoyIQiB1_MVwYv0yCPZxsA0dj}）。
 * 那串东西三个毛病：看不出是谁家的单子、看不出什么时候下的、还可能以 {@code -} 或
 * {@code _} 开头——线上排查时我们真的为「这个前导减号会不会让网关查不到单」浪费过一轮。
 * 现在换成「平台 + 日期 + 时间 + 随机」，四段都有用：</p>
 *
 * <ul>
 *   <li>{@code GOLD} —— 一眼认出是本平台的单子。渠道后台里混着多个商户时这一段最省事。</li>
 *   <li>日期与时间 —— 用户报障时报一个订单号，就等于同时给了时间窗，
 *       直接拿去 grep 日志或按 {@code created_at} 筛库。</li>
 *   <li>随机段 —— 六位，够让订单号不可枚举。真撞上了有 {@code uq_payment_order_out_trade_no}
 *       兜底，下单会失败而不是串单。</li>
 * </ul>
 *
 * <p><b>时间用 Asia/Shanghai 而不是 UTC。</b>后端整体跑在 UTC 上，但订单号是给人看的：
 * 管理台账那一列时间按浏览器本地时区渲染，订单号里再写 UTC 就会和紧挨着的时间列差出 8 小时，
 * 比不写时间更容易把人带偏。本产品只面向国内（手机号 CHECK、易支付、国家统计局区划），
 * 这不是假设。</p>
 *
 * <p>字母表与 {@code ActivationCodeGenerator} 一致：剔除 I/L/O/U 与 0/1，
 * 免得用户手抄或口述时把 0 和 O、1 和 I 弄混。订单号是会被念出来的东西。</p>
 *
 * <p><b>不解析、不回读。</b>订单号只是个标识符，任何地方都不该从它身上取日期或平台名——
 * 那些信息库里都有，且更准。存量的 Base64 订单号也一直有效，
 * {@code PaymentOrderStore} 的合法性正则同时认这两种形状。</p>
 */
@Component
class OutTradeNoGenerator {

    private static final String PREFIX = "GOLD";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int RANDOM_LENGTH = 6;

    private final Clock clock;
    private final SecureRandom secureRandom;

    OutTradeNoGenerator(Clock clock, SecureRandom secureRandom) {
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    String generate() {
        StringBuilder outTradeNo = new StringBuilder(PREFIX)
                .append('-')
                .append(STAMP.format(LocalDateTime.now(clock.withZone(ZONE))))
                .append('-');
        for (int position = 0; position < RANDOM_LENGTH; position++) {
            outTradeNo.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return outTradeNo.toString();
    }
}
