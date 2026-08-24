package com.love.archive.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.love.archive.common.web.ApiException;
import org.junit.jupiter.api.Test;

/**
 * 访客密码策略的边界。
 *
 * <p>下限是个安全相关的常量，而且**同一个数字写在三处**：这里、
 * {@code apps/guest-app/src/validators/registration.ts}、以及注册页输入框的提示文案。
 * 只改一处不会有任何编译或测试错误，症状是用户填了前端说可以的密码、后端却拒绝。
 * 这个类把后端那一份钉住，前端那两份由 {@code registration.test.ts} 钉。</p>
 */
class PasswordPolicyTest {

    /** 8 位是产品定的下限，7 位就该拒。 */
    @Test
    void acceptsEightCharactersAndRefusesSeven() {
        assertThatCode(() -> PasswordPolicy.validate("abc12345")).doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.validate("abc1234"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("PASSWORD_POLICY_VIOLATION");
    }

    @Test
    void acceptsOneHundredTwentyEightAndRefusesOneMore() {
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(127) + "1"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.validate("a".repeat(128) + "1"))
                .isInstanceOf(ApiException.class);
    }

    /** 长度够也必须字母数字都有——降下限的时候这条没跟着放松。 */
    @Test
    void stillRequiresBothLettersAndDigits() {
        assertThatThrownBy(() -> PasswordPolicy.validate("abcdefghij"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("1234567890"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void refusesNull() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null))
                .isInstanceOf(ApiException.class);
    }

    /** 报错文案要说出真实下限，否则用户照着提示改还是过不了。 */
    @Test
    void theMessageQuotesTheRealLowerBound() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short1"))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown ->
                        assertThat(((ApiException) thrown).getMessage()).contains("8 至 128"));
    }
}
