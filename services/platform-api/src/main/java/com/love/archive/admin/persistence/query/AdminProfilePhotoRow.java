package com.love.archive.admin.persistence.query;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminProfilePhotoRow {

    private Long id;
    private String category;
    private String objectKey;
    private Integer sortOrder;
}
