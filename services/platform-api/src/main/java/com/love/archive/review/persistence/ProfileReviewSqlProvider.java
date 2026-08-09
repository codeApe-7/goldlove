package com.love.archive.review.persistence;

import com.love.archive.review.application.DeadlineFilter;
import com.love.archive.review.application.ProfileReviewFilter;
import java.time.OffsetDateTime;

public final class ProfileReviewSqlProvider {

    private static final String DUE_SOON_WINDOW = "INTERVAL '6 hours'";

    public String search(ProfileReviewFilter filter, OffsetDateTime now) {
        return """
                SELECT r.id AS revision_id, r.revision_number, r.status,
                       r.submitted_at, r.review_deadline_at,
                       p.profile_no, p.current_approved_revision_id
                  FROM profile_revision r
                  JOIN guest_profile p ON p.id = r.guest_profile_id
                """ + whereClause(filter, now) + "\n ORDER BY r.review_deadline_at ASC, r.id ASC";
    }

    public String count(ProfileReviewFilter filter, OffsetDateTime now) {
        return """
                SELECT COUNT(*)
                  FROM profile_revision r
                  JOIN guest_profile p ON p.id = r.guest_profile_id
                """ + whereClause(filter, now);
    }

    private static String whereClause(ProfileReviewFilter filter, OffsetDateTime now) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        if (filter.status() != null) {
            sql.append("\nAND r.status = #{filter.status}");
        }
        if (filter.deadline() == DeadlineFilter.OVERDUE) {
            sql.append("\nAND r.review_deadline_at < #{now}");
        } else if (filter.deadline() == DeadlineFilter.DUE_SOON) {
            sql.append("\nAND r.review_deadline_at >= #{now}");
            sql.append("\nAND r.review_deadline_at <= #{now} + ").append(DUE_SOON_WINDOW);
        }
        if (filter.submittedFrom() != null) {
            sql.append("\nAND r.submitted_at >= #{filter.submittedFrom}");
        }
        if (filter.submittedUntil() != null) {
            sql.append("\nAND r.submitted_at < #{filter.submittedUntil}");
        }
        if (filter.profileNo() != null) {
            sql.append("\nAND p.profile_no = #{filter.profileNo}");
        }
        return sql.toString();
    }
}
