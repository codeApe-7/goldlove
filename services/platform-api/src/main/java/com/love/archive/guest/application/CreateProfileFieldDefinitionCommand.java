package com.love.archive.guest.application;

import com.love.archive.guest.domain.ProfileFieldType;
import java.util.List;

public record CreateProfileFieldDefinitionCommand(
        String fieldCode,
        String label,
        ProfileFieldType dataType,
        boolean required,
        List<String> options,
        int sortOrder,
        String instructions) {

    public CreateProfileFieldDefinitionCommand {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
