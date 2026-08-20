package com.love.archive.admin.persistence.query;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminProfileDetailRow {

    private Long id;
    private UUID profileNo;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long userAccountId;
    private String phone;
    private String membershipTier;
    private Long membershipCreditMinor;
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
