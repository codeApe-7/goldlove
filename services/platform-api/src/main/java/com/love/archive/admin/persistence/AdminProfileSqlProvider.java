package com.love.archive.admin.persistence;

import com.love.archive.admin.application.AdminProfileFilter;

/**
 * 管理端档案列表的动态 SQL。跨表读取是 admin 模块的既定做法
 * （{@link AdminDashboardMapper} 同样直接读其他模块的表），仓库规矩是不用 Mapper XML。
 *
 * <p>分页写成显式 LIMIT/OFFSET：MyBatis-Plus 的分页拦截器不会给自定义语句上的
 * IPage 参数加边界，漏掉就会静默返回全表。</p>
 */
public final class AdminProfileSqlProvider {

    private static final String FROM = """
              FROM guest_profile p
              JOIN user_account a ON a.id = p.user_account_id
            """;

    public String search(AdminProfileFilter filter, long limit, long offset) {
        return """
                SELECT p.id, p.profile_no, p.status, p.updated_at,
                       a.id AS user_account_id, a.phone, a.membership_tier
                """ + FROM + whereClause(filter)
                + "\n ORDER BY p.updated_at DESC, p.id DESC"
                + "\n LIMIT #{limit} OFFSET #{offset}";
    }

    public String count(AdminProfileFilter filter) {
        return "SELECT COUNT(*)" + FROM + whereClause(filter);
    }

    private static String whereClause(AdminProfileFilter filter) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        if (filter.phone() != null) {
            sql.append("\nAND a.phone LIKE #{filter.phoneLike}");
        }
        if (filter.status() != null) {
            sql.append("\nAND p.status = #{filter.status}");
        }
        if (filter.membershipTier() != null) {
            sql.append("\nAND a.membership_tier = #{filter.membershipTier}");
        }
        return sql.toString();
    }
}
