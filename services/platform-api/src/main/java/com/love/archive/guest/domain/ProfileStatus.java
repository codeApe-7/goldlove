package com.love.archive.guest.domain;

/** 档案没有审核环节，状态只区分「必填项是否齐全」，保存时算出来。 */
public enum ProfileStatus {
    DRAFT,
    COMPLETED
}
