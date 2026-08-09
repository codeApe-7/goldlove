package com.love.archive.review.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProfileRevisionMapper extends BaseMapper<ProfileRevisionEntity> {

    @Select("""
            SELECT COALESCE(MAX(revision_number), 0) + 1
            FROM profile_revision
            WHERE guest_profile_id = #{profileId}
            """)
    int selectNextRevisionNumber(@Param("profileId") long profileId);

    @Select("""
            SELECT r.id, r.guest_profile_id, r.revision_number, r.gender, r.birth_date,
                   r.height_cm, r.education, r.occupation, r.income_range, r.city,
                   r.wechat_id_ciphertext, r.wechat_id_hmac, r.douyin_id_ciphertext,
                   r.douyin_id_hmac, r.douyin_nickname_ciphertext,
                   r.douyin_profile_url_ciphertext, r.status,
                   r.submitted_by_account_id, r.submitted_at, r.review_deadline_at,
                   r.reviewed_at, r.submission_key_hmac, r.request_payload_sha256,
                   r.version, r.created_at
            FROM profile_revision r
            JOIN guest_profile p ON p.id = r.guest_profile_id
            WHERE r.id = #{revisionId}
              AND p.user_account_id = #{accountId}
            """)
    ProfileRevisionEntity selectOwnedRevision(
            @Param("accountId") long accountId,
            @Param("revisionId") long revisionId);
}
