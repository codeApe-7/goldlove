package com.love.archive.admin.persistence;

import com.love.archive.admin.application.AdminProfileFilter;
import java.util.List;

/**
 * 管理端档案列表的动态 SQL。跨表读取是 admin 模块的既定做法
 * （{@link AdminDashboardMapper} 同样直接读其他模块的表），仓库规矩是不用 Mapper XML。
 *
 * <p>分页写成显式 LIMIT/OFFSET：MyBatis-Plus 的分页拦截器不会给自定义语句上的
 * IPage 参数加边界，漏掉就会静默返回全表。</p>
 *
 * <p>ORDER BY 片段只来自 {@code AdminProfileSort} 枚举常量，筛选值一律走 {@code #{}} 占位符，
 * provider 里不拼任何用户输入。</p>
 */
public final class AdminProfileSqlProvider {

    private static final String FROM = """
              FROM guest_profile p
              JOIN user_account a ON a.id = p.user_account_id
            """;

    private static final String LIST_COLUMNS = """
            SELECT p.id, p.profile_no::text AS profile_no, p.status,
                   p.gender, p.birth_date, p.city,
                   p.created_at, p.updated_at,
                   a.id AS user_account_id, a.phone, a.membership_tier,
                   a.status AS account_status
            """;

    /** 导出与详情共用一份列清单，落在同一个 row 类上，避免两处各写一遍字段。 */
    private static final String FULL_COLUMNS = """
            SELECT p.id, p.profile_no::text AS profile_no, p.status,
                   p.created_at, p.updated_at,
                   p.gender, p.birth_date, p.height_cm, p.education, p.occupation,
                   p.income_range, p.city, p.wechat_id, p.douyin_id,
                   p.douyin_nickname, p.douyin_profile_url,
                   a.id AS user_account_id, a.phone, a.membership_tier,
                   a.membership_credit_minor, a.status AS account_status
            """;

    public String search(AdminProfileFilter filter, long limit, long offset) {
        return LIST_COLUMNS + FROM + whereClause(filter)
                + "\n ORDER BY " + filter.sort().orderBy()
                + "\n LIMIT #{limit} OFFSET #{offset}";
    }

    public String count(AdminProfileFilter filter) {
        return "SELECT COUNT(*)" + FROM + whereClause(filter);
    }

    /**
     * 各 tab 的数量一次查完。五次独立 COUNT 是五个来回，
     * FILTER 聚合只扫一遍同样的结果集。
     */
    public String counts(AdminProfileFilter filter) {
        return """
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE p.status = 'DRAFT') AS draft,
                       COUNT(*) FILTER (WHERE p.status = 'COMPLETED') AS completed,
                       COUNT(*) FILTER (WHERE a.status = 'SUSPENDED') AS suspended,
                       COUNT(*) FILTER (WHERE a.membership_tier <> 'FREE') AS paid
                """ + FROM + whereClause(filter);
    }

    public String export(AdminProfileFilter filter, List<Long> ids, long limit) {
        return FULL_COLUMNS + FROM + whereClause(filter) + idsClause(ids)
                + "\n ORDER BY " + filter.sort().orderBy()
                + "\n LIMIT #{limit}";
    }

    private static String whereClause(AdminProfileFilter filter) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        if (filter.keyword() != null) {
            // 一个输入框同时匹配手机号与档案编号；UUID 要转文本才能做片段匹配。
            sql.append("\nAND (a.phone LIKE #{filter.keywordLike}")
                    .append(" OR p.profile_no::text LIKE #{filter.keywordLike})");
        }
        if (filter.status() != null) {
            sql.append("\nAND p.status = #{filter.status}");
        }
        if (filter.accountStatus() != null) {
            sql.append("\nAND a.status = #{filter.accountStatus}");
        }
        if (filter.membershipTier() != null) {
            sql.append("\nAND a.membership_tier = #{filter.membershipTier}");
        }
        if (filter.paidOnly()) {
            sql.append("\nAND a.membership_tier <> 'FREE'");
        }
        if (filter.city() != null) {
            sql.append("\nAND p.city LIKE #{filter.cityPrefix}");
        }
        if (filter.createdFrom() != null) {
            sql.append("\nAND p.created_at >= #{filter.createdFrom}");
        }
        if (filter.createdTo() != null) {
            sql.append("\nAND p.created_at <= #{filter.createdTo}");
        }
        return sql.toString();
    }

    private static String idsClause(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder sql = new StringBuilder("\nAND p.id IN (");
        for (int index = 0; index < ids.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            sql.append("#{ids[").append(index).append("]}");
        }
        return sql.append(')').toString();
    }
}
