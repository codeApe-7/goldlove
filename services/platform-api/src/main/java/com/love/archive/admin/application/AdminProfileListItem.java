package com.love.archive.admin.application;

import java.time.OffsetDateTime;

/**
 * 档案列表行。
 *
 * <p>规范图的表格主列里有姓名与所属红娘，本产品都没有对应数据：档案不收姓名，
 * 也没有运营人员归属关系。身份列用手机号 + 档案编号。</p>
 *
 * @param accountId     账号 ID——停用/启用是账号级别的操作，前端要拿它调接口
 * @param accountStatus ACTIVE / SUSPENDED / CLOSED
 * @param status        档案完成度 DRAFT / COMPLETED
 */
public record AdminProfileListItem(
        long id,
        String profileNo,
        long accountId,
        String phone,
        String membershipTier,
        String accountStatus,
        String status,
        String gender,
        Integer age,
        String city,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
