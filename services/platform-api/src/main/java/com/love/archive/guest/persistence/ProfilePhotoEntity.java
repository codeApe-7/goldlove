package com.love.archive.guest.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.guest.domain.PhotoCategory;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("profile_photo")
public class ProfilePhotoEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("guest_profile_id")
    private Long guestProfileId;
    @TableField("category")
    private PhotoCategory category;
    @TableField("object_key")
    private String objectKey;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
