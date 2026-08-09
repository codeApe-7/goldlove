package com.love.archive.guest.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.guest.persistence.ProfileFieldDefinitionEntity;
import com.love.archive.guest.persistence.ProfileFieldDefinitionMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class GuestFieldDefinitionQuery {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final ProfileFieldDefinitionMapper definitionMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<GuestFieldDefinitionView> listEnabled() {
        return definitionMapper.selectList(Wrappers.<ProfileFieldDefinitionEntity>lambdaQuery()
                        .eq(ProfileFieldDefinitionEntity::getEnabled, true)
                        .orderByAsc(ProfileFieldDefinitionEntity::getSortOrder)
                        .orderByAsc(ProfileFieldDefinitionEntity::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    private GuestFieldDefinitionView toView(ProfileFieldDefinitionEntity definition) {
        return new GuestFieldDefinitionView(
                definition.getId(),
                definition.getFieldCode(),
                definition.getLabel(),
                definition.getDataType(),
                definition.getRequired(),
                readOptions(definition.getOptionsJson()),
                definition.getSortOrder(),
                definition.getInstructions());
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
}
