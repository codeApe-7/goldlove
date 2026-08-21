package com.love.archive.admin.persistence.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminProfileFieldRow {

    private String fieldCode;
    private String label;
    private String dataType;
    private String textValue;
    private Long integerValue;
    private BigDecimal decimalValue;
    private LocalDate dateValue;
    private Boolean booleanValue;
    private String optionValue;
}
