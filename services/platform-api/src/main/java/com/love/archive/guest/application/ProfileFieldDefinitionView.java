package com.love.archive.guest.application;

import com.love.archive.guest.domain.FieldStorageKind;
import com.love.archive.guest.domain.ProfileFieldType;
import java.util.List;

public record ProfileFieldDefinitionView(
        long id,
        String fieldCode,
        String label,
        FieldStorageKind storageKind,
        ProfileFieldType dataType,
        boolean required,
        boolean enabled,
        List<String> options,
        int sortOrder,
        String instructions,
        long version) {

    public ProfileFieldDefinitionView {
        options = List.copyOf(options);
    }
}
