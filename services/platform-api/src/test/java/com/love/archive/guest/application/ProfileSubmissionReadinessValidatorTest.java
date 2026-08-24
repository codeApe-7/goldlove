package com.love.archive.guest.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.love.archive.guest.domain.FieldStorageKind;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.ProfileFieldDefinitionEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileSubmissionReadinessValidatorTest {

    private final ProfileSubmissionReadinessValidator validator =
            new ProfileSubmissionReadinessValidator();

    @Test
    void recognizesProtectedWechatAsPresentAndIgnoresOptionalSocialAccounts() {
        GuestProfileEntity profile = new GuestProfileEntity();
        profile.setWechatId("wx-real-id");

        List<String> missing = validator.missingRequiredFieldCodes(
                profile,
                List.of(
                        coreDefinition(1L, "wechat_id", true, 80),
                        coreDefinition(2L, "douyin_id", false, 90)),
                List.of());

        assertThat(missing).isEmpty();
    }

    /**
     * 年龄必须被 {@code coreValuePresent} 认识。
     *
     * <p>没写进那个 switch 的 CORE 字段会落到 {@code default -> false}，于是**填了也算没填**：
     * 档案永远停在「还缺年龄」，状态永远升不到 COMPLETED，而表单上那一格明明是有值的。
     * birth_date 换成 age 时漏改这一处不会有任何编译错误。</p>
     */
    @Test
    void countsAgeAsFilledOnceTheGuestEnteredIt() {
        GuestProfileEntity blank = new GuestProfileEntity();
        GuestProfileEntity filled = new GuestProfileEntity();
        filled.setAge(31);
        List<ProfileFieldDefinitionEntity> definitions =
                List.of(coreDefinition(1L, "age", true, 20));

        assertThat(validator.missingRequiredFieldCodes(blank, definitions, List.of()))
                .containsExactly("age");
        assertThat(validator.missingRequiredFieldCodes(filled, definitions, List.of()))
                .isEmpty();
    }

    private static ProfileFieldDefinitionEntity coreDefinition(
            long id, String fieldCode, boolean required, int sortOrder) {
        ProfileFieldDefinitionEntity definition = new ProfileFieldDefinitionEntity();
        definition.setId(id);
        definition.setFieldCode(fieldCode);
        definition.setStorageKind(FieldStorageKind.CORE);
        definition.setRequired(required);
        definition.setEnabled(true);
        definition.setSortOrder(sortOrder);
        return definition;
    }
}
