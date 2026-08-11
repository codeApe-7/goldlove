package com.love.archive.guest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

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
import com.love.archive.guest.persistence.ProfileFieldDefinitionEntity;
import com.love.archive.guest.persistence.ProfileFieldDefinitionMapper;
import com.love.archive.guest.persistence.ProfileFieldValueEntity;
import com.love.archive.guest.persistence.ProfileFieldValueMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GuestProfileDraftServiceTest extends ApiIntegrationTest {

    private static final String REQUEST_ID = "req-task-4";
    private static final long FIRST_USE_GATE = 4_004_001L;

    @Autowired private GuestProfileDraftService service;
    @Autowired private ProfileFieldDefinitionService definitionService;
    @Autowired private GuestProfileMapper profileMapper;
    @Autowired private ProfileFieldDefinitionMapper definitionMapper;
    @Autowired private ProfileFieldValueMapper valueMapper;
    @Autowired private UserAccountMapper accountMapper;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private AuditLogMapper auditMapper;

    private long adminId;
    private long accountId;

    @BeforeEach
    void cleanState() {
        resetDatabase();
        resetGenderDefinition();
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
    void rejectsDynamicIdentityChangeAfterItsFirstValueWasCleared() {
        long definitionId = createDefinition("task4_ever_used", ProfileFieldType.TEXT, List.of());
        service.save(accountId, withDynamicFields(null,
                List.of(new TextFieldInput("task4_ever_used", "present"))), REQUEST_ID);
        service.save(accountId, withDynamicFields(0L, List.of()), REQUEST_ID);
        UpdateProfileFieldDefinitionCommand metadataOnly =
                new UpdateProfileFieldDefinitionCommand(0L, "Ever used", null, null,
                        null, null, null);

        assertCode(() -> definitionService.update(
                        adminId, definitionId, "task4_ever_used_renamed", FieldStorageKind.DYNAMIC,
                        ProfileFieldType.TEXT, metadataOnly, REQUEST_ID),
                "FIELD_DEFINITION_IMMUTABLE");
        assertCode(() -> definitionService.update(
                        adminId, definitionId, "task4_ever_used", FieldStorageKind.DYNAMIC,
                        ProfileFieldType.INTEGER, metadataOnly, REQUEST_ID),
                "FIELD_DEFINITION_IMMUTABLE");
    }

    @Test
    void serializesFirstUseAgainstDefinitionIdentityUpdate() throws Exception {
        long definitionId = createDefinition("task4_first_use_race", ProfileFieldType.TEXT, List.of());
        installFirstUseGate();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection gate = ownerConnection()) {
            acquireFirstUseGate(gate);
            Future<GuestProfileDraftView> save = executor.submit(() -> service.save(
                    accountId,
                    withDynamicFields(null,
                            List.of(new TextFieldInput("task4_first_use_race", "present"))),
                    REQUEST_ID));
            awaitCondition(() -> hasWaitingAdvisoryLock(gate));

            Future<ProfileFieldDefinitionView> identityUpdate = executor.submit(() ->
                    definitionService.update(
                            adminId, definitionId, "task4_first_use_renamed",
                            FieldStorageKind.DYNAMIC, ProfileFieldType.TEXT,
                            new UpdateProfileFieldDefinitionCommand(
                                    0L, null, null, null, null, null, null),
                            REQUEST_ID));
            awaitCondition(() -> identityUpdate.isDone() || hasWaitingDefinitionLock(gate));
            releaseFirstUseGate(gate);

            assertThat(save.get(5, TimeUnit.SECONDS).dynamicFields())
                    .extracting(ProfileFieldValueView::fieldCode)
                    .containsExactly("task4_first_use_race");
            Throwable failure = catchThrowable(() -> identityUpdate.get(5, TimeUnit.SECONDS));
            assertThat(failure).hasCauseInstanceOf(ApiException.class);
            assertThat(((ApiException) failure.getCause()).code())
                    .isEqualTo("FIELD_DEFINITION_IMMUTABLE");
            assertThat(definitionMapper.selectById(definitionId).getFieldCode())
                    .isEqualTo("task4_first_use_race");
        } finally {
            executor.shutdownNow();
            removeFirstUseGate();
        }
    }

    @Test
    void acceptsGenderOptionAddedToTheCoreDefinition() {
        updateGenderOptions(List.of("男", "女", "其他"));

        GuestProfileDraftView saved = service.save(
                accountId, withGender(null, "其他"), REQUEST_ID);

        assertThat(saved.gender()).isEqualTo("其他");
    }

    @Test
    void rejectsGenderOptionRemovedFromTheCoreDefinition() {
        updateGenderOptions(List.of("男"));

        assertCode(() -> service.save(accountId, withGender(null, "女"), REQUEST_ID),
                "FIELD_VALUE_INVALID");
    }

    @Test
    void rejectsGenderWhenCoreDefinitionOptionsAreMissing() {
        definitionMapper.update(Wrappers.<ProfileFieldDefinitionEntity>lambdaUpdate()
                .eq(ProfileFieldDefinitionEntity::getFieldCode, "gender")
                .set(ProfileFieldDefinitionEntity::getOptionsJson, null));

        assertCode(() -> service.save(accountId, withGender(null, "男"), REQUEST_ID),
                "FIELD_VALUE_INVALID");
    }

    @Test
    void rejectsGenderWhenCoreDefinitionTypeIsMalformed() {
        definitionMapper.update(Wrappers.<ProfileFieldDefinitionEntity>lambdaUpdate()
                .eq(ProfileFieldDefinitionEntity::getFieldCode, "gender")
                .set(ProfileFieldDefinitionEntity::getDataType, ProfileFieldType.TEXT)
                .set(ProfileFieldDefinitionEntity::getOptionsJson, null));

        assertCode(() -> service.save(accountId, withGender(null, "男"), REQUEST_ID),
                "FIELD_VALUE_INVALID");
    }

    @Test
    void acceptsIncomeRangeOptionFromTheCoreDefinition() {
        GuestProfileDraftView saved = service.save(
                accountId, withIncomeRange(null, "保密"), REQUEST_ID);

        assertThat(saved.incomeRange()).isEqualTo("保密");
    }

    @Test
    void rejectsIncomeRangeNotInCoreOptions() {
        assertCode(() -> service.save(accountId, withIncomeRange(null, "999万"), REQUEST_ID),
                "FIELD_VALUE_INVALID");
    }

    @Test
    void rejectsChangingTextDefinitionToOptionWithoutOptions() {
        long definitionId = createDefinition(
                "task4_text_to_option", ProfileFieldType.TEXT, List.of());

        assertCode(() -> definitionService.update(
                        adminId, definitionId, "task4_text_to_option", FieldStorageKind.DYNAMIC,
                        ProfileFieldType.SINGLE_OPTION,
                        new UpdateProfileFieldDefinitionCommand(
                                0L, null, null, null, null, null, null),
                        REQUEST_ID),
                "FIELD_VALUE_INVALID");
    }

    @Test
    void rejectsChangingOptionDefinitionToTextWithResidualOptions() {
        long definitionId = createDefinition(
                "task4_option_to_text", ProfileFieldType.SINGLE_OPTION, List.of("A", "B"));

        assertCode(() -> definitionService.update(
                        adminId, definitionId, "task4_option_to_text", FieldStorageKind.DYNAMIC,
                        ProfileFieldType.TEXT,
                        new UpdateProfileFieldDefinitionCommand(
                                0L, null, null, null, null, null, null),
                        REQUEST_ID),
                "FIELD_VALUE_INVALID");
    }

    @Test
    void acceptsChangingUnusedDefinitionTypeWithValidCandidateOptions() {
        long definitionId = createDefinition(
                "task4_valid_type_change", ProfileFieldType.TEXT, List.of());

        ProfileFieldDefinitionView updated = definitionService.update(
                adminId, definitionId, "task4_valid_type_change", FieldStorageKind.DYNAMIC,
                ProfileFieldType.SINGLE_OPTION,
                new UpdateProfileFieldDefinitionCommand(
                        0L, null, null, null, List.of("A", "B"), null, null),
                REQUEST_ID);

        assertThat(updated.dataType()).isEqualTo(ProfileFieldType.SINGLE_OPTION);
        assertThat(updated.options()).containsExactly("A", "B");
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

    private SaveGuestProfileCommand withGender(Long version, String gender) {
        SaveGuestProfileCommand base = validCommand(version);
        return new SaveGuestProfileCommand(
                base.expectedVersion(), gender, base.birthDate(), base.heightCm(),
                base.education(), base.occupation(), base.incomeRange(), base.city(),
                base.wechatId(), base.douyinId(), base.douyinNickname(),
                base.douyinProfileUrl(), base.dynamicFields());
    }

    private SaveGuestProfileCommand withIncomeRange(Long version, String incomeRange) {
        SaveGuestProfileCommand base = validCommand(version);
        return new SaveGuestProfileCommand(
                base.expectedVersion(), base.gender(), base.birthDate(), base.heightCm(),
                base.education(), base.occupation(), incomeRange, base.city(),
                base.wechatId(), base.douyinId(), base.douyinNickname(),
                base.douyinProfileUrl(), base.dynamicFields());
    }

    private void updateGenderOptions(List<String> options) {
        ProfileFieldDefinitionView gender = definitionService.list(1, 100).items().stream()
                .filter(field -> field.fieldCode().equals("gender"))
                .findFirst()
                .orElseThrow();
        definitionService.update(
                adminId,
                gender.id(),
                new UpdateProfileFieldDefinitionCommand(
                        gender.version(), null, null, null, options, null, null),
                REQUEST_ID);
    }

    private void resetGenderDefinition() {
        definitionMapper.update(Wrappers.<ProfileFieldDefinitionEntity>lambdaUpdate()
                .eq(ProfileFieldDefinitionEntity::getFieldCode, "gender")
                .set(ProfileFieldDefinitionEntity::getDataType, ProfileFieldType.SINGLE_OPTION)
                .set(ProfileFieldDefinitionEntity::getRequired, true)
                .set(ProfileFieldDefinitionEntity::getEnabled, true)
                .set(ProfileFieldDefinitionEntity::getOptionsJson, "[\"男\", \"女\"]")
                .set(ProfileFieldDefinitionEntity::getVersion, 0L));
    }

    private void installFirstUseGate() throws SQLException {
        try (Connection owner = ownerConnection();
                PreparedStatement function = owner.prepareStatement("""
                        CREATE OR REPLACE FUNCTION gate_profile_field_first_use_for_test()
                        RETURNS trigger AS $$
                        BEGIN
                            PERFORM pg_advisory_xact_lock(4004001);
                            RETURN NEW;
                        END;
                        $$ LANGUAGE plpgsql
                        """);
                PreparedStatement trigger = owner.prepareStatement("""
                        CREATE TRIGGER gate_profile_field_first_use_for_test
                        BEFORE INSERT ON profile_field_value
                        FOR EACH ROW EXECUTE FUNCTION gate_profile_field_first_use_for_test()
                        """)) {
            function.executeUpdate();
            trigger.executeUpdate();
        }
    }

    private void removeFirstUseGate() throws SQLException {
        try (Connection owner = ownerConnection();
                PreparedStatement trigger = owner.prepareStatement("""
                        DROP TRIGGER IF EXISTS gate_profile_field_first_use_for_test
                        ON profile_field_value
                        """);
                PreparedStatement function = owner.prepareStatement("""
                        DROP FUNCTION IF EXISTS gate_profile_field_first_use_for_test()
                        """)) {
            trigger.executeUpdate();
            function.executeUpdate();
        }
    }

    private static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void acquireFirstUseGate(Connection connection) throws SQLException {
        executeAdvisoryLock(connection, "SELECT pg_advisory_lock(?)");
    }

    private static void releaseFirstUseGate(Connection connection) throws SQLException {
        executeAdvisoryLock(connection, "SELECT pg_advisory_unlock(?)");
    }

    private static void executeAdvisoryLock(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, FIRST_USE_GATE);
            statement.execute();
        }
    }

    private static boolean hasWaitingAdvisoryLock(Connection connection) throws SQLException {
        return queryExists(connection, """
                SELECT EXISTS (
                    SELECT 1 FROM pg_locks
                    WHERE locktype = 'advisory' AND NOT granted
                )
                """);
    }

    private static boolean hasWaitingDefinitionLock(Connection connection) throws SQLException {
        return queryExists(connection, """
                SELECT EXISTS (
                    SELECT 1 FROM pg_stat_activity
                    WHERE pid <> pg_backend_pid()
                      AND datname = current_database()
                      AND wait_event_type = 'Lock'
                      AND query ILIKE '%profile_field_definition%'
                )
                """);
    }

    private static boolean queryExists(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getBoolean(1);
        }
    }

    private static void awaitCondition(CheckedCondition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.evaluate()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for controlled database interleaving");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
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

    @FunctionalInterface
    private interface CheckedCondition {
        boolean evaluate() throws Exception;
    }
}
