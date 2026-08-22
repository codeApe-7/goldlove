package com.love.archive.admin.persistence.query;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminProfileListRow {

    private Long id;
    /** 档案编号。SQL 里已 ::text——UUID 字段在自定义映射里会被静默置 null。 */
    private String profileNo;
    private String status;
    private String gender;
    private LocalDate birthDate;
    private String city;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long userAccountId;
    private String phone;
    private String membershipTier;
    private String accountStatus;
}
