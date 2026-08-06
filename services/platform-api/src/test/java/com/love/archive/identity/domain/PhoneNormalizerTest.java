package com.love.archive.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNormalizerTest {

    private final PhoneNormalizer normalizer = new PhoneNormalizer();

    @ParameterizedTest
    @ValueSource(strings = {
            "13800138000",
            "+86 138-0013-8000",
            "0086 138 0013 8000",
            "86-138-0013-8000"
    })
    void normalizesMainlandChinaMobileNumbers(String input) {
        assertThat(normalizer.normalize(input)).isEqualTo("13800138000");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "12800138000", "1380013800", "138001380000", "+1 202 555 0182", "13800abc000"})
    void rejectsInvalidPhoneNumbers(String input) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> normalizer.normalize(input))
                .withMessage("手机号格式不正确");
    }
}
