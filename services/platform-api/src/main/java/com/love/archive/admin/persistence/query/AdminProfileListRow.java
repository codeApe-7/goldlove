package com.love.archive.admin.persistence.query;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminProfileListRow {

    private Long id;
    private UUID profileNo;
    private String status;
    private OffsetDateTime updatedAt;
    private Long userAccountId;
    private String phone;
    private String membershipTier;
}
