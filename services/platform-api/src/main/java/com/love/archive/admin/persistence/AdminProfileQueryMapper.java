package com.love.archive.admin.persistence;

import com.love.archive.admin.application.AdminProfileFilter;
import com.love.archive.admin.persistence.query.AdminProfileCountsRow;
import com.love.archive.admin.persistence.query.AdminProfileDetailRow;
import com.love.archive.admin.persistence.query.AdminProfileFieldRow;
import com.love.archive.admin.persistence.query.AdminProfileListRow;
import com.love.archive.admin.persistence.query.AdminProfilePhotoRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface AdminProfileQueryMapper {

    @SelectProvider(type = AdminProfileSqlProvider.class, method = "search")
    List<AdminProfileListRow> search(
            @Param("filter") AdminProfileFilter filter,
            @Param("limit") long limit,
            @Param("offset") long offset);

    @SelectProvider(type = AdminProfileSqlProvider.class, method = "count")
    long count(@Param("filter") AdminProfileFilter filter);

    @SelectProvider(type = AdminProfileSqlProvider.class, method = "counts")
    AdminProfileCountsRow counts(@Param("filter") AdminProfileFilter filter);

    /** 导出：列与详情一致，因此复用 AdminProfileDetailRow。ids 为空表示导出整个筛选结果。 */
    @SelectProvider(type = AdminProfileSqlProvider.class, method = "export")
    List<AdminProfileDetailRow> export(
            @Param("filter") AdminProfileFilter filter,
            @Param("ids") List<Long> ids,
            @Param("limit") long limit);

    @Select("""
            SELECT p.id, p.profile_no::text AS profile_no, p.status,
                   p.created_at, p.updated_at,
                   p.gender, p.age, p.height_cm, p.education, p.occupation,
                   p.income_range, p.city, p.wechat_id, p.douyin_id,
                   a.id AS user_account_id, a.phone, a.membership_tier,
                   a.membership_credit_minor, a.status AS account_status
              FROM guest_profile p
              JOIN user_account a ON a.id = p.user_account_id
             WHERE p.id = #{profileId}
            """)
    AdminProfileDetailRow detail(@Param("profileId") long profileId);

    @Select("""
            SELECT d.field_code, d.label, d.data_type,
                   v.text_value, v.integer_value, v.decimal_value,
                   v.date_value, v.boolean_value, v.option_value
              FROM profile_field_value v
              JOIN profile_field_definition d ON d.id = v.field_definition_id
             WHERE v.guest_profile_id = #{profileId}
             ORDER BY d.sort_order, d.id
            """)
    List<AdminProfileFieldRow> dynamicFields(@Param("profileId") long profileId);

    @Select("""
            SELECT id, category, object_key, sort_order
              FROM profile_photo
             WHERE guest_profile_id = #{profileId}
             ORDER BY category, sort_order, id
            """)
    List<AdminProfilePhotoRow> photos(@Param("profileId") long profileId);
}
