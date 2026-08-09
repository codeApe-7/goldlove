package com.love.archive.guest.application;

import java.util.List;

public record UpdateProfileFieldDefinitionCommand(
        Long expectedVersion,
        String label,
        Boolean required,
        Boolean enabled,
        List<String> options,
        Integer sortOrder,
        String instructions) {

    public UpdateProfileFieldDefinitionCommand {
        options = options == null ? null : List.copyOf(options);
    }
}
