package com.love.archive.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.love.archive.common.security.SensitiveValueProtector;
import com.love.archive.guest.application.GuestProfileSnapshot;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class CanonicalSnapshotHasherTest {

    private static final String ENCRYPTION_KEY =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String HMAC_KEY =
            "//////////////////////////////////////////8=";
    private static final ProtectedPlaintext BASE = new ProtectedPlaintext(
            "微信号-甲", "抖音号-甲", "昵称🙂", "https://www.douyin.com/user/甲");

    private AnnotationConfigApplicationContext context;
    private SensitiveValueProtector protector;
    private CanonicalSnapshotHasher hasher;

    @BeforeEach
    void createHasherWithRealProtector() {
        protector = new SensitiveValueProtector(
                ENCRYPTION_KEY, HMAC_KEY, new SecureRandom());
        context = new AnnotationConfigApplicationContext();
        context.registerBean(SensitiveValueProtector.class, () -> protector);
        context.registerBean(CanonicalSnapshotHasher.class);
        context.refresh();
        hasher = context.getBean(CanonicalSnapshotHasher.class);
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void ignoresFreshGcmNoncesForTheSameNormalizedProtectedPlaintext() {
        GuestProfileSnapshot first = snapshot(BASE);
        GuestProfileSnapshot second = snapshot(BASE);

        assertThat(second.wechatIdCiphertext()).isNotEqualTo(first.wechatIdCiphertext());
        assertThat(second.douyinIdCiphertext()).isNotEqualTo(first.douyinIdCiphertext());
        assertThat(second.douyinNicknameCiphertext())
                .isNotEqualTo(first.douyinNicknameCiphertext());
        assertThat(second.douyinProfileUrlCiphertext())
                .isNotEqualTo(first.douyinProfileUrlCiphertext());
        assertThat(hasher.sha256(second)).isEqualTo(hasher.sha256(first));
    }

    @ParameterizedTest
    @EnumSource(ProtectedField.class)
    void changesDigestWhenAnyNormalizedProtectedPlaintextChanges(
            ProtectedField changedField) {
        ProtectedPlaintext changed = switch (changedField) {
            case WECHAT_ID -> new ProtectedPlaintext(
                    "微信号-乙", BASE.douyinId(), BASE.nickname(), BASE.profileUrl());
            case DOUYIN_ID -> new ProtectedPlaintext(
                    BASE.wechatId(), "抖音号-乙", BASE.nickname(), BASE.profileUrl());
            case DOUYIN_NICKNAME -> new ProtectedPlaintext(
                    BASE.wechatId(), BASE.douyinId(), "另一个昵称🙂", BASE.profileUrl());
            case DOUYIN_PROFILE_URL -> new ProtectedPlaintext(
                    BASE.wechatId(), BASE.douyinId(), BASE.nickname(),
                    "https://www.douyin.com/user/乙");
        };

        assertThat(hasher.sha256(snapshot(changed)))
                .isNotEqualTo(hasher.sha256(snapshot(BASE)));
    }

    @Test
    void framesNullProtectedValuesAndEncryptionRejectsBlankPlaintext() {
        ProtectedPlaintext absent = new ProtectedPlaintext(null, null, null, null);

        assertThat(hasher.sha256(snapshot(absent)))
                .isEqualTo(hasher.sha256(snapshot(absent)));
        assertThatThrownBy(() -> protector.encrypt("profile:douyin-nickname", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsClosedWhenAProtectedCiphertextCannotBeAuthenticated() {
        GuestProfileSnapshot valid = snapshot(BASE);
        GuestProfileSnapshot malformed = snapshot(
                valid.wechatIdCiphertext(),
                valid.wechatIdHmac(),
                valid.douyinIdCiphertext(),
                valid.douyinIdHmac(),
                new byte[] {1, 2, 3},
                valid.douyinProfileUrlCiphertext());

        assertThatThrownBy(() -> hasher.sha256(malformed))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private GuestProfileSnapshot snapshot(ProtectedPlaintext values) {
        return snapshot(
                encrypt("profile:wechat-id", values.wechatId()),
                hmac("profile:wechat-id", values.wechatId()),
                encrypt("profile:douyin-id", values.douyinId()),
                hmac("profile:douyin-id", values.douyinId()),
                encrypt("profile:douyin-nickname", values.nickname()),
                encrypt("profile:douyin-profile-url", values.profileUrl()));
    }

    private static GuestProfileSnapshot snapshot(
            byte[] wechatCiphertext,
            String wechatHmac,
            byte[] douyinCiphertext,
            String douyinHmac,
            byte[] nicknameCiphertext,
            byte[] profileUrlCiphertext) {
        return new GuestProfileSnapshot(
                1L,
                10L,
                2L,
                null,
                "女",
                LocalDate.of(1996, 8, 9),
                168,
                "硕士",
                "设计师",
                "30-40万",
                "杭州🙂",
                wechatCiphertext,
                wechatHmac,
                douyinCiphertext,
                douyinHmac,
                nicknameCiphertext,
                profileUrlCiphertext,
                List.of(new GuestProfileSnapshot.FieldValue(
                        "自定义_中文",
                        "自我介绍🙂",
                        "TEXT",
                        null,
                        "喜欢徒步与阅读",
                        null,
                        null,
                        null,
                        null,
                        null)),
                List.of());
    }

    private byte[] encrypt(String domain, String value) {
        return value == null ? null : protector.encrypt(domain, value);
    }

    private String hmac(String domain, String value) {
        return value == null
                ? null
                : protector.hmac(domain, value.toLowerCase(Locale.ROOT));
    }

    private record ProtectedPlaintext(
            String wechatId,
            String douyinId,
            String nickname,
            String profileUrl) {
    }

    private enum ProtectedField {
        WECHAT_ID,
        DOUYIN_ID,
        DOUYIN_NICKNAME,
        DOUYIN_PROFILE_URL
    }
}
