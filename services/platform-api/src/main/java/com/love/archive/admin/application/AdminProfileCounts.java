package com.love.archive.admin.application;

/**
 * 档案列表各 tab 的数量。计数用的是「筛选条上的条件」，不含 tab 自身的条件——
 * 否则切到「草稿」以后其他 tab 的数字会全变成 0。
 */
public record AdminProfileCounts(
        long total, long draft, long completed, long suspended, long paid) {
}
