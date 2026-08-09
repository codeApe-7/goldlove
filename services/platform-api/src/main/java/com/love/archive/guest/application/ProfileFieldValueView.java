package com.love.archive.guest.application;

import com.love.archive.guest.domain.ProfileFieldType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfileFieldValueView(
        String fieldCode,
        ProfileFieldType dataType,
        String textValue,
        Long integerValue,
        BigDecimal decimalValue,
        LocalDate dateValue,
        Boolean booleanValue,
        String optionValue) {
}
