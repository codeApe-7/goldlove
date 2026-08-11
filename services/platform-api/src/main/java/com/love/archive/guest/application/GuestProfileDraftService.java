package com.love.archive.guest.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.security.SensitiveValueProtector;
import com.love.archive.common.web.ApiException;
import com.love.archive.guest.domain.FieldStorageKind;
import com.love.archive.guest.domain.ProfileFieldType;
import com.love.archive.guest.domain.ProfileStatus;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import com.love.archive.guest.persistence.ProfileFieldDefinitionEntity;
import com.love.archive.guest.persistence.ProfileFieldDefinitionMapper;
import com.love.archive.guest.persistence.ProfileFieldValueEntity;
import com.love.archive.guest.persistence.ProfileFieldValueMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestProfileDraftService {

    private static final String WECHAT_ID_DOMAIN = "profile:wechat-id";
    private static final String DOUYIN_ID_DOMAIN = "profile:douyin-id";
    private static final String DOUYIN_NICKNAME_DOMAIN = "profile:douyin-nickname";
    private static final String DOUYIN_PROFILE_URL_DOMAIN = "profile:douyin-profile-url";
    private static final String CORE_GENDER_FIELD_CODE = "gender";
    private static final String CORE_INCOME_RANGE_FIELD_CODE = "income_range";

    private final GuestProfileMapper profileMapper;
    private final ProfileFieldDefinitionMapper definitionMapper;
    private final ProfileFieldValueMapper valueMapper;
    private final SensitiveValueProtector protector;
    private final ProfileSubmissionReadinessValidator readinessValidator;
    private final AuditTrail auditTrail;

    @Transactional(readOnly = true)
    public GuestProfileDraftView get(long accountId) {
        GuestProfileEntity profile = findOwnedProfile(accountId);
        return profile == null ? notStarted() : toView(profile);
    }

    @Transactional
    public GuestProfileDraftView save(
            long accountId,
            SaveGuestProfileCommand command,
            String requestId) {
        NormalizedProfile normalized = normalizeAndValidate(command);
        List<PreparedFieldValue> preparedValues = prepareDynamicValues(command.dynamicFields());
        GuestProfileEntity current = findOwnedProfile(accountId);
        List<ProfileFieldValueEntity> oldValues = current == null
                ? List.of()
                : valuesForProfile(current.getId());
        Map<Long, ProfileFieldDefinitionEntity> definitionsById = definitionsById(
                preparedValues, oldValues);
        Set<String> changedCodes = changedFieldCodes(
                current, oldValues, definitionsById, normalized, preparedValues);
        OffsetDateTime now = OffsetDateTime.now();

        GuestProfileEntity saved;
        if (current == null) {
            if (command.expectedVersion() != null) {
                throw versionConflict();
            }
            saved = insert(accountId, normalized, now);
        } else {
            saved = update(current, command.expectedVersion(), normalized, now);
        }

        replaceDynamicValues(saved.getId(), preparedValues, now);
        appendAudit(accountId, saved.getId(), requestId, changedCodes, now);
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public void validateForSubmission(long accountId) {
        GuestProfileEntity profile = findOwnedProfile(accountId);
        if (profile == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "PROFILE_NOT_STARTED", "请先保存档案草稿");
        }
        List<ProfileFieldDefinitionEntity> definitions = definitionMapper.selectList(
                Wrappers.<ProfileFieldDefinitionEntity>lambdaQuery()
                        .eq(ProfileFieldDefinitionEntity::getEnabled, true)
                        .eq(ProfileFieldDefinitionEntity::getRequired, true)
                        .orderByAsc(ProfileFieldDefinitionEntity::getSortOrder)
                        .orderByAsc(ProfileFieldDefinitionEntity::getId));
        List<String> missing = readinessValidator.missingRequiredFieldCodes(
                profile, definitions, valuesForProfile(profile.getId()));
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "PROFILE_VALIDATION_FAILED",
                    "缺少必填字段: " + String.join(",", missing));
        }
    }

    private GuestProfileEntity insert(long accountId, NormalizedProfile data, OffsetDateTime now) {
        GuestProfileEntity profile = new GuestProfileEntity();
        profile.setProfileNo(UUID.randomUUID());
        profile.setUserAccountId(accountId);
        apply(profile, data);
        profile.setStatus(ProfileStatus.DRAFT);
        profile.setVersion(0L);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        try {
            profileMapper.insert(profile);
        } catch (DataIntegrityViolationException exception) {
            throw versionConflict();
        }
        return profile;
    }

    private GuestProfileEntity update(
            GuestProfileEntity current,
            Long expectedVersion,
            NormalizedProfile data,
            OffsetDateTime now) {
        if (current.getStatus() == ProfileStatus.PENDING_REVIEW) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "PROFILE_REVIEW_IN_PROGRESS", "档案正在审核，暂时不能修改");
        }
        if (current.getStatus() != ProfileStatus.DRAFT
                && current.getStatus() != ProfileStatus.APPROVED
                && current.getStatus() != ProfileStatus.CHANGES_REQUESTED) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "PROFILE_NOT_EDITABLE", "当前档案状态不允许修改");
        }
        if (expectedVersion == null || !expectedVersion.equals(current.getVersion())) {
            throw versionConflict();
        }

        ProtectedValues protectedValues = protect(data);
        int updated = profileMapper.update(Wrappers.<GuestProfileEntity>lambdaUpdate()
                .eq(GuestProfileEntity::getId, current.getId())
                .eq(GuestProfileEntity::getUserAccountId, current.getUserAccountId())
                .eq(GuestProfileEntity::getVersion, expectedVersion)
                .set(GuestProfileEntity::getGender, data.gender())
                .set(GuestProfileEntity::getBirthDate, data.birthDate())
                .set(GuestProfileEntity::getHeightCm, data.heightCm())
                .set(GuestProfileEntity::getEducation, data.education())
                .set(GuestProfileEntity::getOccupation, data.occupation())
                .set(GuestProfileEntity::getIncomeRange, data.incomeRange())
                .set(GuestProfileEntity::getCity, data.city())
                .set(GuestProfileEntity::getWechatIdCiphertext, protectedValues.wechatCiphertext())
                .set(GuestProfileEntity::getWechatIdHmac, protectedValues.wechatHmac())
                .set(GuestProfileEntity::getDouyinIdCiphertext, protectedValues.douyinCiphertext())
                .set(GuestProfileEntity::getDouyinIdHmac, protectedValues.douyinHmac())
                .set(GuestProfileEntity::getDouyinNicknameCiphertext,
                        protectedValues.douyinNicknameCiphertext())
                .set(GuestProfileEntity::getDouyinProfileUrlCiphertext,
                        protectedValues.douyinProfileUrlCiphertext())
                .set(GuestProfileEntity::getStatus, ProfileStatus.DRAFT)
                .set(GuestProfileEntity::getVersion, expectedVersion + 1)
                .set(GuestProfileEntity::getUpdatedAt, now));
        if (updated != 1) {
            throw versionConflict();
        }

        apply(current, data, protectedValues);
        current.setStatus(ProfileStatus.DRAFT);
        current.setVersion(expectedVersion + 1);
        current.setUpdatedAt(now);
        return current;
    }

    private void apply(GuestProfileEntity profile, NormalizedProfile data) {
        apply(profile, data, protect(data));
    }

    private static void apply(
            GuestProfileEntity profile,
            NormalizedProfile data,
            ProtectedValues protectedValues) {
        profile.setGender(data.gender());
        profile.setBirthDate(data.birthDate());
        profile.setHeightCm(data.heightCm());
        profile.setEducation(data.education());
        profile.setOccupation(data.occupation());
        profile.setIncomeRange(data.incomeRange());
        profile.setCity(data.city());
        profile.setWechatIdCiphertext(protectedValues.wechatCiphertext());
        profile.setWechatIdHmac(protectedValues.wechatHmac());
        profile.setDouyinIdCiphertext(protectedValues.douyinCiphertext());
        profile.setDouyinIdHmac(protectedValues.douyinHmac());
        profile.setDouyinNicknameCiphertext(protectedValues.douyinNicknameCiphertext());
        profile.setDouyinProfileUrlCiphertext(protectedValues.douyinProfileUrlCiphertext());
    }

    private ProtectedValues protect(NormalizedProfile data) {
        return new ProtectedValues(
                encrypt(WECHAT_ID_DOMAIN, data.wechatId()),
                hmac(WECHAT_ID_DOMAIN, data.wechatId()),
                encrypt(DOUYIN_ID_DOMAIN, data.douyinId()),
                hmac(DOUYIN_ID_DOMAIN, data.douyinId()),
                encrypt(DOUYIN_NICKNAME_DOMAIN, data.douyinNickname()),
                encrypt(DOUYIN_PROFILE_URL_DOMAIN, data.douyinProfileUrl()));
    }

    private List<PreparedFieldValue> prepareDynamicValues(List<ProfileFieldInput> inputs) {
        if (inputs.isEmpty()) {
            return List.of();
        }
        Map<String, ProfileFieldInput> byCode = new LinkedHashMap<>();
        for (ProfileFieldInput input : inputs) {
            if (input == null || input.fieldCode() == null || input.fieldCode().isBlank()) {
                throw invalidFieldValue("动态字段代码不能为空");
            }
            String code = input.fieldCode().trim();
            if (byCode.putIfAbsent(code, input) != null) {
                throw invalidFieldValue("动态字段不能重复: " + code);
            }
        }

        List<ProfileFieldDefinitionEntity> candidates = definitionMapper.selectList(
                Wrappers.<ProfileFieldDefinitionEntity>lambdaQuery()
                        .in(ProfileFieldDefinitionEntity::getFieldCode, byCode.keySet()));
        Map<String, ProfileFieldDefinitionEntity> definitionsByCode = new HashMap<>();
        for (ProfileFieldDefinitionEntity candidate : candidates.stream()
                .sorted(Comparator.comparing(ProfileFieldDefinitionEntity::getId))
                .toList()) {
            ProfileFieldDefinitionEntity definition =
                    definitionMapper.selectByIdForUpdate(candidate.getId());
            if (definition == null) {
                throw invalidFieldValue("动态字段不可用: " + candidate.getFieldCode());
            }
            definitionsByCode.put(definition.getFieldCode(), definition);
        }

        List<PreparedFieldValue> prepared = new ArrayList<>();
        for (Map.Entry<String, ProfileFieldInput> entry : byCode.entrySet()) {
            ProfileFieldDefinitionEntity definition = definitionsByCode.get(entry.getKey());
            if (definition == null
                    || definition.getStorageKind() != FieldStorageKind.DYNAMIC
                    || !Boolean.TRUE.equals(definition.getEnabled())) {
                throw invalidFieldValue("动态字段不可用: " + entry.getKey());
            }
            prepared.add(new PreparedFieldValue(
                    definition,
                    validatedValue(definition, entry.getValue())));
        }
        markDefinitionsEverUsed(prepared);
        return List.copyOf(prepared);
    }

    private void markDefinitionsEverUsed(List<PreparedFieldValue> preparedValues) {
        for (PreparedFieldValue prepared : preparedValues) {
            ProfileFieldDefinitionEntity definition = prepared.definition();
            if (!Boolean.TRUE.equals(definition.getEverUsed())) {
                int updated = definitionMapper.update(
                        Wrappers.<ProfileFieldDefinitionEntity>lambdaUpdate()
                                .eq(ProfileFieldDefinitionEntity::getId, definition.getId())
                                .set(ProfileFieldDefinitionEntity::getEverUsed, true));
                if (updated != 1) {
                    throw new IllegalStateException("动态字段首次使用标记失败");
                }
                definition.setEverUsed(true);
            }
        }
    }

    private TypedValue validatedValue(
            ProfileFieldDefinitionEntity definition,
            ProfileFieldInput input) {
        ProfileFieldType type = definition.getDataType();
        return switch (type) {
            case TEXT -> new TypedValue(requireDynamicText(input, 1000), null, null, null, null, null);
            case LONG_TEXT -> new TypedValue(requireDynamicText(input, 10000), null, null, null, null, null);
            case INTEGER -> {
                if (!(input instanceof IntegerFieldInput integer) || integer.value() == null) {
                    throw typeMismatch(definition);
                }
                yield new TypedValue(null, integer.value(), null, null, null, null);
            }
            case DECIMAL -> {
                if (!(input instanceof DecimalFieldInput decimal) || decimal.value() == null
                        || decimal.value().precision() > 18 || decimal.value().scale() > 2) {
                    throw typeMismatch(definition);
                }
                yield new TypedValue(null, null, decimal.value(), null, null, null);
            }
            case DATE -> {
                if (!(input instanceof DateFieldInput date) || date.value() == null) {
                    throw typeMismatch(definition);
                }
                yield new TypedValue(null, null, null, date.value(), null, null);
            }
            case BOOLEAN -> {
                if (!(input instanceof BooleanFieldInput bool) || bool.value() == null) {
                    throw typeMismatch(definition);
                }
                yield new TypedValue(null, null, null, null, bool.value(), null);
            }
            case SINGLE_OPTION -> {
                if (!(input instanceof OptionFieldInput option) || option.value() == null) {
                    throw typeMismatch(definition);
                }
                String normalized = option.value().trim();
                List<String> options = readOptions(definition.getOptionsJson());
                if (!options.contains(normalized)) {
                    throw invalidFieldValue("字段选项不合法: " + definition.getFieldCode());
                }
                yield new TypedValue(null, null, null, null, null, normalized);
            }
        };
    }

    private String requireDynamicText(ProfileFieldInput input, int maxLength) {
        if (!(input instanceof TextFieldInput text)
                || text.value() == null
                || text.value().isBlank()
                || text.value().trim().length() > maxLength) {
            throw invalidFieldValue("动态文本字段值不合法");
        }
        return text.value().trim();
    }

    private void replaceDynamicValues(
            long profileId,
            List<PreparedFieldValue> values,
            OffsetDateTime now) {
        valueMapper.delete(Wrappers.<ProfileFieldValueEntity>lambdaQuery()
                .eq(ProfileFieldValueEntity::getGuestProfileId, profileId));
        for (PreparedFieldValue prepared : values) {
            ProfileFieldValueEntity value = toEntity(profileId, prepared, now);
            valueMapper.insert(value);
        }
    }

    private static ProfileFieldValueEntity toEntity(
            long profileId,
            PreparedFieldValue prepared,
            OffsetDateTime now) {
        ProfileFieldValueEntity entity = new ProfileFieldValueEntity();
        entity.setGuestProfileId(profileId);
        entity.setFieldDefinitionId(prepared.definition().getId());
        entity.setTextValue(prepared.value().text());
        entity.setIntegerValue(prepared.value().integer());
        entity.setDecimalValue(prepared.value().decimal());
        entity.setDateValue(prepared.value().date());
        entity.setBooleanValue(prepared.value().bool());
        entity.setOptionValue(prepared.value().option());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private GuestProfileDraftView toView(GuestProfileEntity profile) {
        List<ProfileFieldValueEntity> values = valuesForProfile(profile.getId());
        Map<Long, ProfileFieldDefinitionEntity> definitions = definitionsById(List.of(), values);
        List<ProfileFieldValueView> dynamic = values.stream()
                .filter(value -> definitions.containsKey(value.getFieldDefinitionId()))
                .sorted(Comparator.comparing(value ->
                        definitions.get(value.getFieldDefinitionId()).getSortOrder()))
                .map(value -> {
                    ProfileFieldDefinitionEntity definition = definitions.get(value.getFieldDefinitionId());
                    return new ProfileFieldValueView(
                            definition.getFieldCode(), definition.getDataType(),
                            value.getTextValue(), value.getIntegerValue(), value.getDecimalValue(),
                            value.getDateValue(), value.getBooleanValue(), value.getOptionValue());
                })
                .toList();
        String profileUrl = decrypt(DOUYIN_PROFILE_URL_DOMAIN,
                profile.getDouyinProfileUrlCiphertext());
        return new GuestProfileDraftView(
                profile.getProfileNo(),
                profile.getStatus().name(),
                profile.getVersion(),
                profile.getGender(),
                profile.getBirthDate(),
                profile.getHeightCm(),
                profile.getEducation(),
                profile.getOccupation(),
                profile.getIncomeRange(),
                profile.getCity(),
                decrypt(WECHAT_ID_DOMAIN, profile.getWechatIdCiphertext()),
                decrypt(DOUYIN_ID_DOMAIN, profile.getDouyinIdCiphertext()),
                decrypt(DOUYIN_NICKNAME_DOMAIN, profile.getDouyinNicknameCiphertext()),
                profileUrl == null ? null : URI.create(profileUrl),
                profile.getPendingRevisionId(),
                profile.getCurrentApprovedRevisionId(),
                dynamic);
    }

    private GuestProfileEntity findOwnedProfile(long accountId) {
        return profileMapper.selectOne(Wrappers.<GuestProfileEntity>lambdaQuery()
                .eq(GuestProfileEntity::getUserAccountId, accountId));
    }

    private List<ProfileFieldValueEntity> valuesForProfile(long profileId) {
        return valueMapper.selectList(Wrappers.<ProfileFieldValueEntity>lambdaQuery()
                .eq(ProfileFieldValueEntity::getGuestProfileId, profileId)
                .orderByAsc(ProfileFieldValueEntity::getId));
    }

    private Map<Long, ProfileFieldDefinitionEntity> definitionsById(
            List<PreparedFieldValue> prepared,
            List<ProfileFieldValueEntity> values) {
        Map<Long, ProfileFieldDefinitionEntity> result = new HashMap<>();
        for (PreparedFieldValue item : prepared) {
            result.put(item.definition().getId(), item.definition());
        }
        List<Long> missingIds = values.stream()
                .map(ProfileFieldValueEntity::getFieldDefinitionId)
                .filter(id -> !result.containsKey(id))
                .distinct()
                .toList();
        if (!missingIds.isEmpty()) {
            for (ProfileFieldDefinitionEntity definition : definitionMapper.selectByIds(missingIds)) {
                result.put(definition.getId(), definition);
            }
        }
        return result;
    }

    private Set<String> changedFieldCodes(
            GuestProfileEntity current,
            List<ProfileFieldValueEntity> oldValues,
            Map<Long, ProfileFieldDefinitionEntity> definitions,
            NormalizedProfile data,
            List<PreparedFieldValue> preparedValues) {
        Set<String> changed = new LinkedHashSet<>();
        compare(changed, "gender", current == null ? null : current.getGender(), data.gender());
        compare(changed, "birth_date", current == null ? null : current.getBirthDate(), data.birthDate());
        compare(changed, "height_cm", current == null ? null : current.getHeightCm(), data.heightCm());
        compare(changed, "education", current == null ? null : current.getEducation(), data.education());
        compare(changed, "occupation", current == null ? null : current.getOccupation(), data.occupation());
        compare(changed, "income_range", current == null ? null : current.getIncomeRange(), data.incomeRange());
        compare(changed, "city", current == null ? null : current.getCity(), data.city());
        compare(changed, "wechat_id", current == null ? null : current.getWechatIdHmac(),
                hmac(WECHAT_ID_DOMAIN, data.wechatId()));
        compare(changed, "douyin_id", current == null ? null : current.getDouyinIdHmac(),
                hmac(DOUYIN_ID_DOMAIN, data.douyinId()));
        compare(changed, "douyin_nickname",
                current == null ? null : decrypt(DOUYIN_NICKNAME_DOMAIN,
                        current.getDouyinNicknameCiphertext()), data.douyinNickname());
        compare(changed, "douyin_profile_url",
                current == null ? null : decrypt(DOUYIN_PROFILE_URL_DOMAIN,
                        current.getDouyinProfileUrlCiphertext()), data.douyinProfileUrl());

        Map<Long, TypedValue> oldByDefinition = new HashMap<>();
        for (ProfileFieldValueEntity old : oldValues) {
            oldByDefinition.put(old.getFieldDefinitionId(), typedValue(old));
        }
        Map<Long, TypedValue> newByDefinition = new HashMap<>();
        for (PreparedFieldValue prepared : preparedValues) {
            newByDefinition.put(prepared.definition().getId(), prepared.value());
        }
        Set<Long> definitionIds = new LinkedHashSet<>(oldByDefinition.keySet());
        definitionIds.addAll(newByDefinition.keySet());
        for (Long definitionId : definitionIds) {
            if (!Objects.equals(oldByDefinition.get(definitionId), newByDefinition.get(definitionId))) {
                ProfileFieldDefinitionEntity definition = definitions.get(definitionId);
                if (definition != null) {
                    changed.add(definition.getFieldCode());
                }
            }
        }
        return changed;
    }

    private void appendAudit(
            long accountId,
            long profileId,
            String requestId,
            Set<String> changedCodes,
            OffsetDateTime now) {
        String codesJson = changedCodes.stream()
                .map(code -> "\"" + code + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.GUEST,
                accountId,
                "PROFILE_DRAFT_SAVED",
                "GUEST_PROFILE",
                profileId,
                requestId,
                "{\"changedFieldCodes\":[" + codesJson + "]}",
                now));
    }

    private NormalizedProfile normalizeAndValidate(SaveGuestProfileCommand command) {
        Objects.requireNonNull(command, "command");
        String gender = optionalText(command.gender(), "性别", 32);
        if (gender != null) {
            validateCoreSingleOption(CORE_GENDER_FIELD_CODE, gender);
        }
        if (command.birthDate() != null && command.birthDate().isAfter(LocalDate.now())) {
            throw invalidFieldValue("出生日期不能晚于今天");
        }
        if (command.heightCm() != null
                && (command.heightCm() < 50 || command.heightCm() > 250)) {
            throw invalidFieldValue("身高范围不正确");
        }
        String incomeRange = optionalText(command.incomeRange(), "年薪", 100);
        if (incomeRange != null) {
            validateCoreSingleOption(CORE_INCOME_RANGE_FIELD_CODE, incomeRange);
        }
        String wechatId = optionalText(command.wechatId(), "微信号", 200);
        String douyinId = optionalText(command.douyinId(), "抖音号", 200);
        String profileUrl = normalizeUrl(command.douyinProfileUrl());
        return new NormalizedProfile(
                gender,
                command.birthDate(),
                command.heightCm(),
                optionalText(command.education(), "学历", 100),
                optionalText(command.occupation(), "职业", 200),
                incomeRange,
                optionalText(command.city(), "所在城市", 100),
                wechatId,
                douyinId,
                optionalText(command.douyinNickname(), "抖音昵称", 500),
                profileUrl);
    }

    private void validateCoreSingleOption(String fieldCode, String value) {
        ProfileFieldDefinitionEntity definition = definitionMapper.selectOne(
                Wrappers.<ProfileFieldDefinitionEntity>lambdaQuery()
                        .eq(ProfileFieldDefinitionEntity::getFieldCode, fieldCode));
        if (definition == null
                || definition.getStorageKind() != FieldStorageKind.CORE
                || definition.getDataType() != ProfileFieldType.SINGLE_OPTION
                || !Boolean.TRUE.equals(definition.getEnabled())) {
            throw invalidFieldValue("核心字段配置不合法: " + fieldCode);
        }
        List<String> options;
        try {
            options = readOptions(definition.getOptionsJson());
        } catch (IllegalStateException exception) {
            throw invalidFieldValue("核心字段配置不合法: " + fieldCode);
        }
        if (options.isEmpty() || !options.contains(value)) {
            throw invalidFieldValue("字段选项不合法: " + fieldCode);
        }
    }

    private static String normalizeUrl(URI uri) {
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        String normalized = uri.normalize().toASCIIString();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null
                || normalized.length() > 2048) {
            throw invalidFieldValue("抖音主页链接格式不正确");
        }
        return normalized;
    }

    private static String optionalText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalidFieldValue(label + "过长");
        }
        return normalized;
    }

    private List<String> readOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String content = json.trim();
        if (content.length() < 2 || content.charAt(0) != '[' || content.charAt(content.length() - 1) != ']') {
            throw new IllegalStateException("字段选项数据无法解析");
        }
        if (content.equals("[]")) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        int index = 1;
        while (index < content.length() - 1) {
            while (index < content.length() - 1 && Character.isWhitespace(content.charAt(index))) {
                index++;
            }
            if (content.charAt(index) != '"') {
                throw new IllegalStateException("字段选项数据无法解析");
            }
            StringBuilder option = new StringBuilder();
            index++;
            while (index < content.length() - 1) {
                char character = content.charAt(index++);
                if (character == '"') {
                    break;
                }
                if (character == '\\' && index < content.length() - 1) {
                    character = content.charAt(index++);
                }
                option.append(character);
            }
            options.add(option.toString());
            while (index < content.length() - 1 && Character.isWhitespace(content.charAt(index))) {
                index++;
            }
            if (index < content.length() - 1 && content.charAt(index) == ',') {
                index++;
            }
        }
        return List.copyOf(options);
    }

    private byte[] encrypt(String domain, String value) {
        return value == null ? null : protector.encrypt(domain, value);
    }

    private String hmac(String domain, String value) {
        return value == null ? null : protector.hmac(domain, value.toLowerCase(Locale.ROOT));
    }

    private String decrypt(String domain, byte[] value) {
        return value == null ? null : protector.decrypt(domain, value);
    }

    private static void compare(Set<String> changed, String code, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changed.add(code);
        }
    }

    private static TypedValue typedValue(ProfileFieldValueEntity value) {
        return new TypedValue(
                value.getTextValue(), value.getIntegerValue(), value.getDecimalValue(),
                value.getDateValue(), value.getBooleanValue(), value.getOptionValue());
    }

    private static GuestProfileDraftView notStarted() {
        return new GuestProfileDraftView(
                null, "NOT_STARTED", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, List.of());
    }

    private static ApiException invalidFieldValue(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "FIELD_VALUE_INVALID", message);
    }

    private static ApiException typeMismatch(ProfileFieldDefinitionEntity definition) {
        return invalidFieldValue("字段类型不匹配: " + definition.getFieldCode());
    }

    private static ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT,
                "PROFILE_VERSION_CONFLICT", "档案版本已发生变化，请刷新后重试");
    }

    private record NormalizedProfile(
            String gender,
            LocalDate birthDate,
            Integer heightCm,
            String education,
            String occupation,
            String incomeRange,
            String city,
            String wechatId,
            String douyinId,
            String douyinNickname,
            String douyinProfileUrl) {
    }

    private record ProtectedValues(
            byte[] wechatCiphertext,
            String wechatHmac,
            byte[] douyinCiphertext,
            String douyinHmac,
            byte[] douyinNicknameCiphertext,
            byte[] douyinProfileUrlCiphertext) {
    }

    private record PreparedFieldValue(
            ProfileFieldDefinitionEntity definition,
            TypedValue value) {
    }

    private record TypedValue(
            String text,
            Long integer,
            BigDecimal decimal,
            LocalDate date,
            Boolean bool,
            String option) {
    }
}
