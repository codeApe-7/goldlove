package com.love.archive.guest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.audit.persistence.AuditLogEntity;
import com.love.archive.audit.persistence.AuditLogMapper;
import com.love.archive.common.web.ApiException;
import com.love.archive.guest.domain.FieldStorageKind;
import com.love.archive.guest.domain.ProfileFieldType;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfileFieldValueEntity;
import com.love.archive.guest.persistence.ProfileFieldValueMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GuestProfileDraftServiceTest extends ApiIntegrationTest {

    private static final String REQUEST_ID = "req-task-4";

    @Autowired private GuestProfileDraftService service;
    @Autowired private ProfileFieldDefinitionService definitionService;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private ProfileFieldValueMapper valueMapper;
    @Autowired private UserAccountMapper accountMapper;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private AuditLogMapper auditMapper;

    private long adminId;
    private long accountId;

    @BeforeEach
    void cleanState() {
        resetDatabase();
        adminId = insertAdmin();
        accountId = insertAccount(AccountStatus.ACTIVE, "account-primary");
    }

    @Test
    void getDoesNotCreateAndFirstSaveCreatesAnOwnedDraft() {
        assertThat(service.get(accountId).status()).isEqualTo("NOT_STARTED");
        assertThat(profileMapper.selectCount(Wrappers.lambdaQuery())).isZero();

        GuestProfileDraftView saved = service.save(accountId, validCommand(null), REQUEST_ID);

        assertThat(saved.status()).isEqualTo("DRAFT");
        assertThat(saved.version()).isZero();
        assertThat(profileMapper.selectOne(Wrappers.<GuestProfileEntity>lambdaQuery()
                        .eq(GuestProfileEntity::getUserAccountId, accountId)))
                .extracting(GuestProfileEntity::getUserAccountId)
                .isEqualTo(accountId);
    }

    @Test
    void persistsGeneratedProfileNumberThroughMybatisPlus() {
        GuestProfileDraftView saved = service.save(accountId, validCommand(null), REQUEST_ID);

        GuestProfileEntity persisted = profileMapper.selectOne(
                Wrappers.<GuestProfileEntity>lambdaQuery()
                        .eq(GuestProfileEntity::getUserAccountId, accountId));
        assertThat(persisted.getProfileNo())
                .isNotNull()
                .isEqualTo(saved.profileNo());
    }

    @Test
    void rejectsStaleVersionAndEditsDuringReview() {
        service.save(accountId, validCommand(null), REQUEST_ID);

        assertCode(() -> service.save(accountId, validCommand(99L), REQUEST_ID),
                "PROFILE_VERSION_CONFLICT");

        profileMapper.update(Wrappers.<GuestProfileEntity>lambdaUpdate()
                .eq(GuestProfileEntity::getUserAccountId, accountId)
                .set(GuestProfileEntity::getStatus,
                        com.love.archive.guest.domain.ProfileStatus.PENDING_REVIEW));
        assertCode(() -> service.save(accountId, validCommand(0L), REQUEST_ID),
                "PROFILE_REVIEW_IN_PROGRESS");
    }

    @Test
    void neverLoadsAnotherAccountsDraft() {
        service.save(accountId, validCommand(null), REQUEST_ID);
        long anotherAccountId = insertAccount(AccountStatus.ACTIVE, "account-other");

        GuestProfileDraftView other = service.get(anotherAccountId);

        assertThat(other.status()).isEqualTo("NOT_STARTED");
        assertThat(other.wechatId()).isNull();
        assertThat(other.dynamicFields()).isEmpty();
    }

    @Test
    void rejectsSaveForNonActiveAccount() {
        long suspendedId = insertAccount(AccountStatus.SUSPENDED, "account-suspended");

        assertCode(() -> service.save(suspendedId, validCommand(null), REQUEST_ID),
                "AUTH_ACCOUNT_INACTIVE");
        assertThat(profileMapper.selectCount(Wrappers.<GuestProfileEntity>lambdaQuery()
                        .eq(GuestProfileEntity::getUserAccountId, suspendedId)))
                .isZero();
    }

    @Test
    void reportsEveryMissingCoreFieldAtSubmissionValidation() {
        service.save(accountId, new SaveGuestProfileCommand(
                null, null, null, null, null, null, null, null,
                null, null, null, null, List.of()), REQUEST_ID);

        assertThatThrownBy(() -> service.validateForSubmission(accountId))
                .isInstanceOf(ApiException.class)
                .extracting(Throwable::getMessage)
                .asString()
                .contains("gender", "birth_date", "height_cm", "education",
                        "occupation", "income_range", "city");
    }

    @Test
    void persistsEverySupportedDynamicValueTypeInItsTypedColumn() {
        long textId = createDefinition("task4_text", ProfileFieldType.TEXT, List.of());
        long longTextId = createDefinition("task4_long_text", ProfileFieldType.LONG_TEXT, List.of());
        long integerId = createDefinition("task4_integer", ProfileFieldType.INTEGER, List.of());
        long decimalId = createDefinition("task4_decimal", ProfileFieldType.DECIMAL, List.of());
        long dateId = createDefinition("task4_date", ProfileFieldType.DATE, List.of());
        long booleanId = createDefinition("task4_boolean", ProfileFieldType.BOOLEAN, List.of());
        long optionId = createDefinition("task4_option", ProfileFieldType.SINGLE_OPTION, List.of("A", "B"));

        service.save(accountId, withDynamicFields(null, List.of(
                new TextFieldInput("task4_text", "short"),
                new TextFieldInput("task4_long_text", "long value"),
                new IntegerFieldInput("task4_integer", 42L),
                new DecimalFieldInput("task4_decimal", new BigDecimal("12.34")),
                new DateFieldInput("task4_date", LocalDate.of(2026, 8, 6)),
                new BooleanFieldInput("task4_boolean", true),
                new OptionFieldInput("task4_option", "B"))), REQUEST_ID);

        assertTypedValue(textId, "short", null, null, null, null, null);
        assertTypedValue(longTextId, "long value", null, null, null, null, null);
        assertTypedValue(integerId, null, 42L, null, null, null, null);
        assertTypedValue(decimalId, null, null, new BigDecimal("12.34"), null, null, null);
        assertTypedValue(dateId, null, null, null, LocalDate.of(2026, 8, 6), null, null);
        assertTypedValue(booleanId, null, null, null, null, true, null);
        assertTypedValue(optionId, null, null, null, null, null, "B");
    }

    @Test
    void rejectsOptionOutsideDefinitionOptions() {
        createDefinition("task4_color", ProfileFieldType.SINGLE_OPTION, List.of("red", "blue"));

        assertCode(() -> service.save(accountId, withDynamicFields(null,
                        List.of(new OptionFieldInput("task4_color", "green"))), REQUEST_ID),
                "FIELD_VALUE_INVALID");
    }

    @Test
    void rejectsValueForDisabledDefinition() {
        long definitionId = createDefinition("task4_disabled", ProfileFieldType.TEXT, List.of());
        definitionService.update(adminId, definitionId,
                new UpdateProfileFieldDefinitionCommand(0L, null, null, false, null, null, null),
                REQUEST_ID);

        assertCode(() -> service.save(accountId, withDynamicFields(null,
                        List.of(new TextFieldInput("task4_disabled", "not allowed"))), REQUEST_ID),
                "FIELD_VALUE_INVALID");
    }

    @Test
    void rejectsCoreStorageTypeEnabledAndRequiredChanges() {
        long genderId = definitionService.list(1, 100).items().stream()
                .filter(field -> field.fieldCode().equals("gender"))
                .findFirst().orElseThrow().id();
        UpdateProfileFieldDefinitionCommand forbidden =
                new UpdateProfileFieldDefinitionCommand(0L, null, false, false, null, null, null);

        assertCode(() -> definitionService.update(
                        adminId, genderId, "gender", FieldStorageKind.DYNAMIC,
                        ProfileFieldType.TEXT, forbidden, REQUEST_ID),
                "FIELD_DEFINITION_IMMUTABLE");
    }

    @Test
    void rejectsDynamicCodeOrTypeChangeAfterFirstValue() {
        long definitionId = createDefinition("task4_locked", ProfileFieldType.TEXT, List.of());
        service.save(accountId, withDynamicFields(null,
                List.of(new TextFieldInput("task4_locked", "present"))), REQUEST_ID);
        UpdateProfileFieldDefinitionCommand metadataOnly =
                new UpdateProfileFieldDefinitionCommand(0L, "Locked", null, null, null, null, null);

        assertCode(() -> definitionService.update(
                        adminId, definitionId, "task4_renamed", FieldStorageKind.DYNAMIC,
                        ProfileFieldType.TEXT, metadataOnly, REQUEST_ID),
                "FIELD_DEFINITION_IMMUTABLE");
        assertCode(() -> definitionService.update(
                        adminId, definitionId, "task4_locked", FieldStorageKind.DYNAMIC,
                        ProfileFieldType.INTEGER, metadataOnly, REQUEST_ID),
                "FIELD_DEFINITION_IMMUTABLE");
    }

    @Test
    void encryptsSameIdentifierToDifferentCiphertextAcrossSaves() {
        service.save(accountId, validCommand(null), REQUEST_ID);
        byte[] first = profile().getWechatIdCiphertext();

        service.save(accountId, validCommand(0L), REQUEST_ID);
        byte[] second = profile().getWechatIdCiphertext();

        assertThat(first).isNotEqualTo(second);
        assertThat(service.get(accountId).wechatId()).isEqualTo("wx-private-123");
    }

    @Test
    void auditContainsChangedFieldCodesButNoProtectedPlaintext() {
        service.save(accountId, validCommand(null), REQUEST_ID);

        AuditLogEntity audit = auditMapper.selectOne(Wrappers.<AuditLogEntity>lambdaQuery()
                .eq(AuditLogEntity::getAction, "PROFILE_DRAFT_SAVED")
                .eq(AuditLogEntity::getActorId, accountId));

        assertThat(audit.getMetadata())
                .contains("wechat_id", "douyin_id", "city")
                .doesNotContain("wx-private-123", "dy-private-456", "private nickname");
    }

    private long createDefinition(String code, ProfileFieldType type, List<String> options) {
        return definitionService.create(adminId,
                new CreateProfileFieldDefinitionCommand(
                        code, code, type, false, options, 1000, null), REQUEST_ID).id();
    }

    private void assertTypedValue(
            long definitionId,
            String text,
            Long integer,
            BigDecimal decimal,
            LocalDate date,
            Boolean bool,
            String option) {
        ProfileFieldValueEntity value = valueMapper.selectOne(
                Wrappers.<ProfileFieldValueEntity>lambdaQuery()
                        .eq(ProfileFieldValueEntity::getFieldDefinitionId, definitionId));
        assertThat(value.getTextValue()).isEqualTo(text);
        assertThat(value.getIntegerValue()).isEqualTo(integer);
        if (decimal == null) {
            assertThat(value.getDecimalValue()).isNull();
        } else {
            assertThat(value.getDecimalValue()).isEqualByComparingTo(decimal);
        }
        assertThat(value.getDateValue()).isEqualTo(date);
        assertThat(value.getBooleanValue()).isEqualTo(bool);
        assertThat(value.getOptionValue()).isEqualTo(option);
    }

    private GuestProfileEntity profile() {
        return profileMapper.selectOne(Wrappers.<GuestProfileEntity>lambdaQuery()
                .eq(GuestProfileEntity::getUserAccountId, accountId));
    }

    private SaveGuestProfileCommand validCommand(Long expectedVersion) {
        return new SaveGuestProfileCommand(
                expectedVersion,
                "男",
                LocalDate.of(1995, 5, 20),
                178,
                "本科",
                "工程师",
                "20-30万",
                "杭州",
                "wx-private-123",
                "dy-private-456",
                "private nickname",
                URI.create("https://www.douyin.com/user/private"),
                List.of());
    }

    private SaveGuestProfileCommand withDynamicFields(Long version, List<ProfileFieldInput> fields) {
        SaveGuestProfileCommand base = validCommand(version);
        return new SaveGuestProfileCommand(
                base.expectedVersion(), base.gender(), base.birthDate(), base.heightCm(),
                base.education(), base.occupation(), base.incomeRange(), base.city(),
                base.wechatId(), base.douyinId(), base.douyinNickname(),
                base.douyinProfileUrl(), fields);
    }

    private long insertAdmin() {
        OffsetDateTime now = OffsetDateTime.now();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("task4-admin");
        admin.setDisplayName("Task 4 Admin");
        admin.setPasswordHash("not-used-in-service-test");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
        return admin.getId();
    }

    private long insertAccount(AccountStatus status, String seed) {
        OffsetDateTime now = OffsetDateTime.now();
        UserAccountEntity account = new UserAccountEntity();
        account.setPhoneCiphertext(seed.getBytes(StandardCharsets.UTF_8));
        account.setPhoneHmac(seed);
        account.setPasswordHash("not-used-in-service-test");
        account.setStatus(status);
        account.setCreatedByAdminId(adminId);
        account.setActivatedAt(now);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        accountMapper.insert(account);
        return account.getId();
    }

    private static void assertCode(ThrowingOperation operation, String code) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }
}
