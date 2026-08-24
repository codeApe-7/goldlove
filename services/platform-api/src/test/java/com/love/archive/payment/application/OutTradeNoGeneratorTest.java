package com.love.archive.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 商户订单号的形状。不测「随机够不够随机」——那是 SecureRandom 的事——
 * 只钉住那些一改就会在别处出问题的性质。
 */
class OutTradeNoGeneratorTest {

    /** 与 PaymentOrderStore.OUT_TRADE_NO 保持一致：那道正则拦得住的才查得了库。 */
    private static final Pattern STORE_ACCEPTS = Pattern.compile("[A-Za-z0-9_-]{6,64}");

    private static final Instant AUGUST_24_0235_UTC = Instant.parse("2026-08-24T02:35:12Z");

    private OutTradeNoGenerator generator(Instant now) {
        return new OutTradeNoGenerator(Clock.fixed(now, ZoneOffset.UTC), new SecureRandom());
    }

    @Test
    void carriesThePlatformPrefixAndTheOrderTime() {
        String outTradeNo = generator(AUGUST_24_0235_UTC).generate();

        // 02:35 UTC 就是北京时间当天 10:35——订单号写本地时间，
        // 否则它会和管理台账里按本地时区渲染的时间列差出 8 小时。
        assertThat(outTradeNo).startsWith("GOLD-20260824-103512-");
        assertThat(outTradeNo).hasSize(27);
    }

    /**
     * 跨过 UTC 零点时，订单号里的日期要跟着北京时间走。
     *
     * <p>用 UTC 的话，北京时间 8 月 24 日早上 7 点下的单会写成 {@code 20260823}，
     * 用户拿着「昨天的单号」来对今天的账。</p>
     */
    @Test
    void theDateFollowsBeijingTimeNotUtc() {
        String outTradeNo = generator(Instant.parse("2026-08-23T23:10:00Z")).generate();

        assertThat(outTradeNo).startsWith("GOLD-20260824-071000-");
    }

    /** 字母表剔除了 0/1/I/L/O/U——订单号是会被念出来、手抄进工单的东西。 */
    @Test
    void theRandomTailAvoidsCharactersPeopleMisread() {
        OutTradeNoGenerator generator = generator(AUGUST_24_0235_UTC);

        for (int attempt = 0; attempt < 200; attempt++) {
            String tail = generator.generate().substring("GOLD-20260824-103512-".length());
            assertThat(tail).hasSize(6).doesNotContainAnyWhitespaces();
            assertThat(tail).matches("[23456789ABCDEFGHJKMNPQRSTVWXYZ]{6}");
        }
    }

    /**
     * 同一秒内连着下单也不能撞号。
     *
     * <p>时间段只到秒，所以「不重复」这件事全靠随机段扛。真撞上了有唯一索引兜底，
     * 但那是下单失败，不是串单——这里先保证正常情况下不会走到那一步。</p>
     */
    @Test
    void staysUniqueWithinTheSameSecond() {
        OutTradeNoGenerator generator = generator(AUGUST_24_0235_UTC);
        Set<String> seen = new HashSet<>();

        for (int attempt = 0; attempt < 2000; attempt++) {
            seen.add(generator.generate());
        }

        assertThat(seen).hasSize(2000);
    }

    /**
     * 生成的订单号必须过得了 PaymentOrderStore 的合法性正则。
     *
     * <p>那道正则是查库前的挡板，形状对不上就会直接判成「订单不存在」——
     * 而且是**新下的单**查不到，回调也会被挡在门外。</p>
     */
    @Test
    void passesTheLookupGuardThatStandsInFrontOfTheDatabase() {
        OutTradeNoGenerator generator = generator(AUGUST_24_0235_UTC);

        for (int attempt = 0; attempt < 100; attempt++) {
            assertThat(STORE_ACCEPTS.matcher(generator.generate()).matches()).isTrue();
        }
    }

    /** 存量的 Base64URL 订单号仍然要能被那道正则放行——它们在渠道那边还是活的。 */
    @Test
    void theLookupGuardStillAcceptsLegacyBase64Numbers() {
        for (String legacy : new String[] {
                "BLR2BnyRYINA7ruVuioV_T-GIWi9AKQv",
                "-2EbDnKRoyIQiB1_MVwYv0yCPZxsA0dj"}) {
            assertThat(STORE_ACCEPTS.matcher(legacy).matches())
                    .withFailMessage("存量订单号 %s 被挡住了，这些单子会查不到", legacy)
                    .isTrue();
        }
    }

    /** 订单号要塞进 payment_order.out_trade_no / payment_record.out_trade_no 两个 VARCHAR(64)。 */
    @Test
    void fitsTheDatabaseColumn() {
        assertThat(generator(AUGUST_24_0235_UTC).generate().length()).isLessThanOrEqualTo(64);
    }
}
