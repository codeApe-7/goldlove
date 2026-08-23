package com.love.archive.course.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.love.archive.course.domain.CourseContentType;
import com.love.archive.course.domain.CourseStatus;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("course")
public class CourseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("collection_id")
    private Long collectionId;
    /** 教材名称。 */
    @TableField("title")
    private String title;
    @TableField("subtitle")
    private String subtitle;
    @TableField("summary")
    private String summary;
    /** 讲师。 */
    @TableField("author_name")
    private String authorName;
    @TableField("content_type")
    private CourseContentType contentType;
    @TableField("content_markdown")
    private String contentMarkdown;
    @TableField("cover_object_key")
    private String coverObjectKey;
    @TableField("video_object_key")
    private String videoObjectKey;
    /** 只登记、只展示，不参与任何校验。 */
    @TableField("video_duration_seconds")
    private Integer videoDurationSeconds;
    @TableField("video_size_bytes")
    private Long videoSizeBytes;
    @TableField("video_content_type")
    private String videoContentType;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("status")
    private CourseStatus status;
    @TableField("published_at")
    private OffsetDateTime publishedAt;
    @TableField("created_by_admin_id")
    private Long createdByAdminId;
    @TableField("updated_by_admin_id")
    private Long updatedByAdminId;
    @Version
    @TableField("version")
    private Long version;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
