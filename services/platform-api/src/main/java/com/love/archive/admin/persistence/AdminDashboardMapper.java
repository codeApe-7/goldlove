package com.love.archive.admin.persistence;

import com.love.archive.admin.application.AdminDashboardView;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminDashboardMapper {

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM profile_revision WHERE status = 'PENDING')
                AS pending_reviews,
              (SELECT COUNT(*) FROM user_account
                WHERE created_at >= #{dayStart} AND created_at < #{dayEnd})
                AS today_registrations,
              (SELECT COUNT(*) FROM profile_review_record
                WHERE reviewed_at >= #{dayStart} AND reviewed_at < #{dayEnd})
                AS today_reviews,
              (SELECT COUNT(*) FROM guest_profile) AS total_profiles,
              (SELECT COUNT(*) FROM user_account WHERE membership_tier = 'VIP') AS vip_members,
              (SELECT COUNT(*) FROM user_account WHERE membership_tier = 'SVIP') AS svip_members
            """)
    AdminDashboardView loadStats(
            @Param("dayStart") OffsetDateTime dayStart,
            @Param("dayEnd") OffsetDateTime dayEnd);
}
