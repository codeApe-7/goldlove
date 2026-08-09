package com.love.archive.guest.web;

import com.love.archive.guest.application.CreateProfileFieldDefinitionCommand;
import com.love.archive.guest.domain.ProfileFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateProfileFieldDefinitionRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,63}") String fieldCode,
        @NotBlank @Size(max = 100) String label,
        @NotNull ProfileFieldType dataType,
        boolean required,
        List<@NotBlank @Size(max = 200) String> options,
        int sortOrder,
        @Size(max = 4000) String instructions) {

    CreateProfileFieldDefinitionCommand toCommand() {
        return new CreateProfileFieldDefinitionCommand(
                fieldCode, label, dataType, required, options, sortOrder, instructions);
    }
}
