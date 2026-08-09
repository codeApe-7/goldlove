package com.love.archive.guest.web;

import com.love.archive.guest.application.UpdateProfileFieldDefinitionCommand;
import com.love.archive.guest.domain.FieldStorageKind;
import com.love.archive.guest.domain.ProfileFieldType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateProfileFieldDefinitionRequest(
        @NotNull Long expectedVersion,
        @Pattern(regexp = "[a-z][a-z0-9_]{0,63}") String fieldCode,
        FieldStorageKind storageKind,
        ProfileFieldType dataType,
        @Size(max = 100) String label,
        Boolean required,
        Boolean enabled,
        List<@Size(min = 1, max = 200) String> options,
        Integer sortOrder,
        @Size(max = 4000) String instructions) {

    UpdateProfileFieldDefinitionCommand toCommand() {
        return new UpdateProfileFieldDefinitionCommand(
                expectedVersion, label, required, enabled, options, sortOrder, instructions);
    }
}
