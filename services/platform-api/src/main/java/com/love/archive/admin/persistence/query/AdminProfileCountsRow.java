package com.love.archive.admin.persistence.query;

import lombok.Getter;
import lombok.Setter;

/** 档案列表各 tab 的数量，一条 SQL 用 COUNT FILTER 一次取回。 */
@Getter
@Setter
public class AdminProfileCountsRow {

    private Long total;
    private Long draft;
    private Long completed;
    private Long suspended;
    private Long paid;
}
