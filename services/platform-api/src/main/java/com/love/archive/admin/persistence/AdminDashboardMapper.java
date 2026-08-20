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
              (SELECT COUNT(*) FROM user_account) AS total_accounts,
              (SELECT COUNT(*) FROM user_account
                WHERE created_at >= #{dayStart} AND created_at < #{dayEnd})
                AS today_registrations,
              (SELECT COUNT(*) FROM guest_profile) AS total_profiles,
              (SELECT COUNT(*) FROM guest_profile WHERE status = 'COMPLETED')
                AS completed_profiles,
              (SELECT COUNT(*) FROM user_account WHERE membership_tier = 'VIP') AS vip_members,
              (SELECT COUNT(*) FROM user_account WHERE membership_tier = 'SVIP') AS svip_members,
              (SELECT COALESCE(SUM(amount_minor), 0) FROM payment_record
                WHERE status = 'PAID' AND paid_at >= #{dayStart} AND paid_at < #{dayEnd})
                AS today_paid_amount_minor
            """)
    AdminDashboardView loadStats(
            @Param("dayStart") OffsetDateTime dayStart,
            @Param("dayEnd") OffsetDateTime dayEnd);
}
