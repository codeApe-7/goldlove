package com.love.archive.review.persistence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.love.archive.review.application.ProfileReviewFilter;
import com.love.archive.review.persistence.query.ProfileRevisionFieldRow;
import com.love.archive.review.persistence.query.ProfileReviewHeaderRow;
import com.love.archive.review.persistence.query.ProfileReviewListRow;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface ProfileReviewQueryMapper {

    @SelectProvider(type = ProfileReviewSqlProvider.class, method = "search")
    IPage<ProfileReviewListRow> search(
            IPage<ProfileReviewListRow> page,
            @Param("filter") ProfileReviewFilter filter,
            @Param("now") OffsetDateTime now);

    @SelectProvider(type = ProfileReviewSqlProvider.class, method = "count")
    long count(
            @Param("filter") ProfileReviewFilter filter,
            @Param("now") OffsetDateTime now);

    @Select("""
            SELECT r.id AS revision_id, r.guest_profile_id, r.revision_number, r.status,
                   r.submitted_at, r.review_deadline_at, r.reviewed_at, r.version,
                   p.profile_no, p.current_approved_revision_id
              FROM profile_revision r
              JOIN guest_profile p ON p.id = r.guest_profile_id
             WHERE r.id = #{revisionId}
            """)
    ProfileReviewHeaderRow detailHeader(@Param("revisionId") long revisionId);

    @Select("""
            SELECT profile_revision_id, field_code, field_label, data_type, display_option,
                   text_value, integer_value, decimal_value, date_value, boolean_value,
                   option_value
              FROM profile_revision_field_value
             WHERE profile_revision_id = #{revisionId}
             ORDER BY field_code
            """)
    List<ProfileRevisionFieldRow> revisionFields(@Param("revisionId") long revisionId);

    @Select("SELECT version FROM guest_profile WHERE id = #{profileId}")
    Long profileVersion(@Param("profileId") long profileId);
}
