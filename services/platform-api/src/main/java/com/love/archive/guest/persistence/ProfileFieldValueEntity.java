package com.love.archive.guest.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("profile_field_value")
public class ProfileFieldValueEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("guest_profile_id")
    private Long guestProfileId;
    @TableField("field_definition_id")
    private Long fieldDefinitionId;
    @TableField("text_value")
    private String textValue;
    @TableField("integer_value")
    private Long integerValue;
    @TableField("decimal_value")
    private BigDecimal decimalValue;
    @TableField("date_value")
    private LocalDate dateValue;
    @TableField("boolean_value")
    private Boolean booleanValue;
    @TableField("option_value")
    private String optionValue;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
