package com.love.archive.review.persistence;

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
@TableName("profile_revision_photo")
public class ProfileRevisionPhotoEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("profile_revision_id")
    private Long profileRevisionId;
    @TableField("category")
    private PhotoCategory category;
    @TableField("object_key")
    private String objectKey;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("created_at")
    private OffsetDateTime createdAt;
}
