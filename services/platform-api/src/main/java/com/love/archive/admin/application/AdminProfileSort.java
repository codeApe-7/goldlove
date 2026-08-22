package com.love.archive.admin.application;

/**
 * 档案列表排序方式。ORDER BY 片段只能来自这里的常量——
 * 排序参数是用户可控输入，拼进 SQL 前必须过白名单，用枚举让类型系统把这件事兜住。
 *
 * <p>规范图 3.7 的「姓名 A-Z / Z-A」在本产品没有对应数据（档案里没有姓名列），
 * 换成手机号升降序，档位数量与规范图一致。</p>
 */
public enum AdminProfileSort {
    UPDATED_DESC("p.updated_at DESC, p.id DESC"),
    CREATED_DESC("p.created_at DESC, p.id DESC"),
    CREATED_ASC("p.created_at ASC, p.id ASC"),
    PHONE_ASC("a.phone ASC, p.id ASC"),
    PHONE_DESC("a.phone DESC, p.id DESC");

    private final String orderBy;

    AdminProfileSort(String orderBy) {
        this.orderBy = orderBy;
    }

    public String orderBy() {
        return orderBy;
    }

    public static AdminProfileSort orDefault(AdminProfileSort sort) {
        return sort == null ? UPDATED_DESC : sort;
    }
}
