package com.love.archive.admin.persistence.query;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminProfileDetailRow {

    private Long id;
    /** 档案编号。SQL 里已 ::text——UUID 字段在自定义映射里会被静默置 null。 */
    private String profileNo;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long userAccountId;
    private String phone;
    private String membershipTier;
    private Long membershipCreditMinor;
    /** 账号状态（ACTIVE / SUSPENDED / CLOSED）——停用是账号级别的动作，档案自身没有停用位。 */
    private String accountStatus;
    private String gender;
    private LocalDate birthDate;
    private Integer heightCm;
    private String education;
    private String occupation;
    private String incomeRange;
    private String city;
    private String wechatId;
    private String douyinId;
    private String douyinNickname;
    private String douyinProfileUrl;
}
