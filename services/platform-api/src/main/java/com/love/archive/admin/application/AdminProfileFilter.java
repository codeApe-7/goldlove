package com.love.archive.admin.application;

/**
 * 管理端档案列表筛选条件。
 *
 * @param phone          手机号模糊匹配片段
 * @param status         DRAFT / COMPLETED
 * @param membershipTier FREE / VIP / SVIP
 */
public record AdminProfileFilter(String phone, String status, String membershipTier) {

    /** 供 SQL 直接使用的 LIKE 片段，避免在 provider 里拼字符串。 */
    public String phoneLike() {
        return phone == null ? null : "%" + phone + "%";
    }

    public static AdminProfileFilter of(String phone, String status, String membershipTier) {
        return new AdminProfileFilter(blankToNull(phone), blankToNull(status), blankToNull(membershipTier));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
