package com.love.archive.review.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.review.domain.ReviewResult;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("profile_review_record")
public class ProfileReviewRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("profile_revision_id")
    private Long profileRevisionId;
    @TableField("reviewer_admin_id")
    private Long reviewerAdminId;
    @TableField("result")
    private ReviewResult result;
    @TableField("reason_code")
    private String reasonCode;
    @TableField("comment")
    private String comment;
    @TableField("reviewed_at")
    private OffsetDateTime reviewedAt;
    @TableField("request_id")
    private String requestId;
    @TableField("created_at")
    private OffsetDateTime createdAt;
}
