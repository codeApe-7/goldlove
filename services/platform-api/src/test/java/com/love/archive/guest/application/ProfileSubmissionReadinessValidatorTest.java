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
                        coreDefinition(2L, "douyin_id", false, 90),
                        coreDefinition(3L, "douyin_nickname", false, 100),
                        coreDefinition(4L, "douyin_profile_url", false, 110)),
                List.of());

        assertThat(missing).isEmpty();
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
