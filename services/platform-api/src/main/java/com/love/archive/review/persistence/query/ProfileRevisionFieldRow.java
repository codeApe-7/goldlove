package com.love.archive.review.persistence.query;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfileRevisionFieldRow(
        long profileRevisionId,
        String fieldCode,
        String fieldLabel,
        String dataType,
        String displayOption,
        String textValue,
        Long integerValue,
        BigDecimal decimalValue,
        LocalDate dateValue,
        Boolean booleanValue,
        String optionValue) {
}
