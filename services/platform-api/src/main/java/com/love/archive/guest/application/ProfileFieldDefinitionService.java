package com.love.archive.guest.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.web.ApiException;
import com.love.archive.common.web.PageView;
import com.love.archive.guest.domain.FieldStorageKind;
import com.love.archive.guest.domain.ProfileFieldType;
import com.love.archive.guest.persistence.ProfileFieldDefinitionEntity;
import com.love.archive.guest.persistence.ProfileFieldDefinitionMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ProfileFieldDefinitionService {

    private static final Pattern FIELD_CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final ProfileFieldDefinitionMapper definitionMapper;
    private final AuditTrail auditTrail;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageView<ProfileFieldDefinitionView> list(long requestedPage, long requestedSize) {
        long pageNumber = Math.max(1, requestedPage);
        long pageSize = Math.min(100, Math.max(1, requestedSize));
        Page<ProfileFieldDefinitionEntity> page = definitionMapper.selectPage(
                Page.of(pageNumber, pageSize),
                Wrappers.<ProfileFieldDefinitionEntity>lambdaQuery()
                        .orderByAsc(ProfileFieldDefinitionEntity::getSortOrder)
                        .orderByAsc(ProfileFieldDefinitionEntity::getId));
        return new PageView<>(page.getRecords().stream().map(this::toView).toList(),
                pageNumber, pageSize, page.getTotal());
    }

    @Transactional
    public ProfileFieldDefinitionView create(
            long adminId,
            CreateProfileFieldDefinitionCommand command,
            String requestId) {
        String fieldCode = requireFieldCode(command.fieldCode());
        String label = requireText(command.label(), "字段名称", 100);
        ProfileFieldType dataType = Objects.requireNonNull(command.dataType(), "dataType");
        List<String> options = validateOptions(dataType, command.options());
        String instructions = optionalText(command.instructions(), "字段说明", 4000);

        ProfileFieldDefinitionEntity definition = new ProfileFieldDefinitionEntity();
        definition.setFieldCode(fieldCode);
        definition.setLabel(label);
        definition.setStorageKind(FieldStorageKind.DYNAMIC);
        definition.setDataType(dataType);
        definition.setRequired(command.required());
        definition.setEnabled(true);
        definition.setEverUsed(false);
        definition.setOptionsJson(writeOptions(options));
        definition.setSortOrder(command.sortOrder());
        definition.setInstructions(instructions);
        definition.setVersion(0L);
        OffsetDateTime now = OffsetDateTime.now();
        definition.setCreatedAt(now);
        definition.setUpdatedAt(now);
        try {
            definitionMapper.insert(definition);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "FIELD_DEFINITION_ALREADY_EXISTS", "字段代码已存在");
        }

        appendAudit(adminId, "PROFILE_FIELD_DEFINITION_CREATED", definition, requestId, now);
        return toView(definition);
    }

    @Transactional
    public ProfileFieldDefinitionView update(
            long adminId,
            long definitionId,
            UpdateProfileFieldDefinitionCommand command,
            String requestId) {
        return update(adminId, definitionId, null, null, null, command, requestId);
    }

    @Transactional
    public ProfileFieldDefinitionView update(
            long adminId,
            long definitionId,
            String requestedFieldCode,
            FieldStorageKind requestedStorageKind,
            ProfileFieldType requestedDataType,
            UpdateProfileFieldDefinitionCommand command,
            String requestId) {
        ProfileFieldDefinitionEntity current = requireDefinitionForUpdate(definitionId);
        if (command.expectedVersion() == null) {
            throw versionConflict();
        }

        String nextCode = requestedFieldCode == null
                ? current.getFieldCode()
                : requireFieldCode(requestedFieldCode);
        FieldStorageKind nextStorage = requestedStorageKind == null
                ? current.getStorageKind()
                : requestedStorageKind;
        ProfileFieldType nextType = requestedDataType == null
                ? current.getDataType()
                : requestedDataType;
        boolean changesIdentity = !nextCode.equals(current.getFieldCode())
                || nextStorage != current.getStorageKind()
                || nextType != current.getDataType();

        enforceMutability(current, command, changesIdentity, nextStorage);
        if (changesIdentity && Boolean.TRUE.equals(current.getEverUsed())) {
            throw immutableDefinition();
        }

        String label = command.label() == null
                ? current.getLabel()
                : requireText(command.label(), "字段名称", 100);
        boolean required = command.required() == null ? current.getRequired() : command.required();
        boolean enabled = command.enabled() == null ? current.getEnabled() : command.enabled();
        List<String> candidateOptions = command.options() == null
                ? readOptions(current.getOptionsJson())
                : command.options();
        List<String> options = validateOptions(nextType, candidateOptions);
        int sortOrder = command.sortOrder() == null ? current.getSortOrder() : command.sortOrder();
        String instructions = command.instructions() == null
                ? current.getInstructions()
                : optionalText(command.instructions(), "字段说明", 4000);
        OffsetDateTime now = OffsetDateTime.now();

        int updated;
        try {
            updated = definitionMapper.update(Wrappers.<ProfileFieldDefinitionEntity>lambdaUpdate()
                    .eq(ProfileFieldDefinitionEntity::getId, definitionId)
                    .eq(ProfileFieldDefinitionEntity::getVersion, command.expectedVersion())
                    .set(ProfileFieldDefinitionEntity::getFieldCode, nextCode)
                    .set(ProfileFieldDefinitionEntity::getStorageKind, nextStorage)
                    .set(ProfileFieldDefinitionEntity::getDataType, nextType)
                    .set(ProfileFieldDefinitionEntity::getLabel, label)
                    .set(ProfileFieldDefinitionEntity::getRequired, required)
                    .set(ProfileFieldDefinitionEntity::getEnabled, enabled)
                    .set(ProfileFieldDefinitionEntity::getOptionsJson, writeOptions(options))
                    .set(ProfileFieldDefinitionEntity::getSortOrder, sortOrder)
                    .set(ProfileFieldDefinitionEntity::getInstructions, instructions)
                    .set(ProfileFieldDefinitionEntity::getVersion, command.expectedVersion() + 1)
                    .set(ProfileFieldDefinitionEntity::getUpdatedAt, now));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "FIELD_DEFINITION_ALREADY_EXISTS", "字段代码已存在");
        }
        if (updated != 1) {
            throw versionConflict();
        }

        ProfileFieldDefinitionEntity saved = requireDefinition(definitionId);
        appendAudit(adminId, "PROFILE_FIELD_DEFINITION_UPDATED", saved, requestId, now);
        return toView(saved);
    }

    private void enforceMutability(
            ProfileFieldDefinitionEntity current,
            UpdateProfileFieldDefinitionCommand command,
            boolean changesIdentity,
            FieldStorageKind nextStorage) {
        if (nextStorage != current.getStorageKind()) {
            throw immutableDefinition();
        }
        if (current.getStorageKind() == FieldStorageKind.CORE) {
            if (changesIdentity
                    || (command.required() != null && command.required() != current.getRequired())
                    || (command.enabled() != null && command.enabled() != current.getEnabled())) {
                throw immutableDefinition();
            }
        }
    }

    private ProfileFieldDefinitionEntity requireDefinition(long definitionId) {
        ProfileFieldDefinitionEntity definition = definitionMapper.selectById(definitionId);
        if (definition == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "FIELD_DEFINITION_NOT_FOUND", "字段定义不存在");
        }
        return definition;
    }

    private ProfileFieldDefinitionEntity requireDefinitionForUpdate(long definitionId) {
        ProfileFieldDefinitionEntity definition = definitionMapper.selectByIdForUpdate(definitionId);
        if (definition == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "FIELD_DEFINITION_NOT_FOUND", "字段定义不存在");
        }
        return definition;
    }

    private ProfileFieldDefinitionView toView(ProfileFieldDefinitionEntity definition) {
        return new ProfileFieldDefinitionView(
                definition.getId(),
                definition.getFieldCode(),
                definition.getLabel(),
                definition.getStorageKind(),
                definition.getDataType(),
                definition.getRequired(),
                definition.getEnabled(),
                readOptions(definition.getOptionsJson()),
                definition.getSortOrder(),
                definition.getInstructions(),
                definition.getVersion());
    }

    private void appendAudit(
            long adminId,
            String action,
            ProfileFieldDefinitionEntity definition,
            String requestId,
            OffsetDateTime now) {
        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                action,
                "PROFILE_FIELD_DEFINITION",
                definition.getId(),
                requestId,
                "{\"fieldCode\":\"" + definition.getFieldCode() + "\"}",
                now));
    }

    private List<String> validateOptions(ProfileFieldType type, List<String> rawOptions) {
        List<String> options = rawOptions == null ? List.of() : rawOptions.stream()
                .map(option -> requireText(option, "选项", 200))
                .toList();
        if (new LinkedHashSet<>(options).size() != options.size()) {
            throw invalidDefinition("字段选项不能重复");
        }
        if (type == ProfileFieldType.SINGLE_OPTION && options.isEmpty()) {
            throw invalidDefinition("单选字段必须配置选项");
        }
        if (type != ProfileFieldType.SINGLE_OPTION && !options.isEmpty()) {
            throw invalidDefinition("只有单选字段可以配置选项");
        }
        return options;
    }

    private String writeOptions(List<String> options) {
        if (options.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JacksonException exception) {
            throw new IllegalStateException("字段选项序列化失败", exception);
        }
    }

    private List<String> readOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(optionsJson, STRING_LIST);
        } catch (JacksonException exception) {
            throw new IllegalStateException("字段选项数据无法解析", exception);
        }
    }

    private static String requireFieldCode(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!FIELD_CODE.matcher(normalized).matches()) {
            throw invalidDefinition("字段代码格式不正确");
        }
        return normalized;
    }

    private static String requireText(String value, String label, int maxLength) {
        String normalized = optionalText(value, label, maxLength);
        if (normalized == null) {
            throw invalidDefinition(label + "不能为空");
        }
        return normalized;
    }

    private static String optionalText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalidDefinition(label + "过长");
        }
        return normalized;
    }

    private static ApiException invalidDefinition(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "FIELD_VALUE_INVALID", message);
    }

    private static ApiException immutableDefinition() {
        return new ApiException(HttpStatus.CONFLICT,
                "FIELD_DEFINITION_IMMUTABLE", "字段定义的受保护属性不可修改");
    }

    private static ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT,
                "PROFILE_VERSION_CONFLICT", "字段定义版本已发生变化，请刷新后重试");
    }
}
