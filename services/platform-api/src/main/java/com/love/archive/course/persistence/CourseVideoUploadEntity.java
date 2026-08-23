package com.love.archive.course.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.course.domain.VideoUploadStatus;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("course_video_upload")
public class CourseVideoUploadEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** COS 生成的 uploadId。 */
    @TableField("upload_id")
    private String uploadId;
    @TableField("object_key")
    private String objectKey;
    /** 开会话的管理员。uploadId 会回到浏览器，续传时必须核对是不是本人。 */
    @TableField("admin_id")
    private Long adminId;
    @TableField("original_filename")
    private String originalFilename;
    @TableField("content_type")
    private String contentType;
    @TableField("status")
    private VideoUploadStatus status;
    @TableField("declared_total_bytes")
    private Long declaredTotalBytes;
    @TableField("uploaded_bytes")
    private Long uploadedBytes;
    @TableField("part_count")
    private Integer partCount;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
