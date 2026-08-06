package com.love.archive.review.persistence;

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
@TableName("profile_revision_field_value")
public class ProfileRevisionFieldValueEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("profile_revision_id")
    private Long profileRevisionId;
    @TableField("field_code")
    private String fieldCode;
    @TableField("field_label")
    private String fieldLabel;
    @TableField("data_type")
    private String dataType;
    @TableField("display_option")
    private String displayOption;
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
}
