package com.love.archive.admin.persistence.query;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminActivationCodeRow {

    private Long id;
    private String code;
    private String boundPhone;
    private Boolean boundPhoneRegistered;
    private String grantedTier;
    private String status;
    private String note;
    private OffsetDateTime createdAt;
    private OffsetDateTime redeemedAt;
    private Long redeemedByAccountId;
    private String redeemedPhone;
}
