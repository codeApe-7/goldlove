package com.love.archive.admin.application;

import java.time.OffsetDateTime;

/**
 * 管理端档案列表筛选条件。
 *
 * @param keyword        手机号或档案编号的片段（一个输入框同时匹配两者）
 * @param status         档案完成度：DRAFT / COMPLETED
 * @param accountStatus  账号状态：ACTIVE / SUSPENDED / CLOSED
 * @param membershipTier FREE / VIP / SVIP
 * @param paidOnly       只看付费会员（VIP 或 SVIP）；与 membershipTier 互不冲突，可叠加
 * @param city           所在地区前缀，如「北京市」或「北京市 / 东城区」
 * @param createdFrom    创建时间下界（含）
 * @param createdTo      创建时间上界（含）
 * @param sort           排序方式
 */
public record AdminProfileFilter(
        String keyword,
        String status,
        String accountStatus,
        String membershipTier,
        boolean paidOnly,
        String city,
        OffsetDateTime createdFrom,
        OffsetDateTime createdTo,
        AdminProfileSort sort) {

    /** 供 SQL 直接使用的 LIKE 片段，避免在 provider 里拼字符串。 */
    public String keywordLike() {
        return keyword == null ? null : "%" + keyword + "%";
    }

    /**
     * 地区按前缀匹配：档案的 city 存的是「北京市 / 东城区」这样拼好的文本，
     * 只选到省或市时要能命中它下面的全部区县。
     */
    public String cityPrefix() {
        return city == null ? null : city + "%";
    }

    public static AdminProfileFilter of(
            String keyword,
            String status,
            String accountStatus,
            String membershipTier,
            Boolean paidOnly,
            String city,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            AdminProfileSort sort) {
        return new AdminProfileFilter(
                blankToNull(keyword),
                blankToNull(status),
                blankToNull(accountStatus),
                blankToNull(membershipTier),
                Boolean.TRUE.equals(paidOnly),
                blankToNull(city),
                createdFrom,
                createdTo,
                AdminProfileSort.orDefault(sort));
    }

    /** 统计各 tab 数量时用：清掉 tab 自身的条件，只保留筛选条上的条件。 */
    public AdminProfileFilter withoutTabConditions() {
        return new AdminProfileFilter(
                keyword, null, null, membershipTier, false, city, createdFrom, createdTo, sort);
    }

    public AdminProfileFilter withStatus(String value) {
        return new AdminProfileFilter(
                keyword, value, accountStatus, membershipTier, paidOnly, city, createdFrom, createdTo, sort);
    }

    public AdminProfileFilter withAccountStatus(String value) {
        return new AdminProfileFilter(
                keyword, status, value, membershipTier, paidOnly, city, createdFrom, createdTo, sort);
    }

    public AdminProfileFilter withPaidOnly(boolean value) {
        return new AdminProfileFilter(
                keyword, status, accountStatus, membershipTier, value, city, createdFrom, createdTo, sort);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
