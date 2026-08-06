package com.love.archive.guest.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.love.archive.guest.domain.FieldStorageKind;
import com.love.archive.guest.domain.ProfileFieldType;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("profile_field_definition")
public class ProfileFieldDefinitionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("field_code")
    private String fieldCode;
    @TableField("label")
    private String label;
    @TableField("storage_kind")
    private FieldStorageKind storageKind;
    @TableField("data_type")
    private ProfileFieldType dataType;
    @TableField("required")
    private Boolean required;
    @TableField("enabled")
    private Boolean enabled;
    @TableField("options_json")
    private String optionsJson;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("instructions")
    private String instructions;
    @Version
    @TableField("version")
    private Long version;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
