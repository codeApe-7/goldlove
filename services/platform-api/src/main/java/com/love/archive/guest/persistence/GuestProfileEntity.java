package com.love.archive.guest.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.love.archive.guest.domain.ProfileStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.ObjectTypeHandler;

@Getter
@Setter
@TableName(value = "guest_profile", autoResultMap = true)
public class GuestProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(value = "profile_no", typeHandler = ObjectTypeHandler.class)
    private UUID profileNo;
    @TableField("user_account_id")
    private Long userAccountId;
    @TableField("gender")
    private String gender;
    @TableField("birth_date")
    private LocalDate birthDate;
    @TableField("height_cm")
    private Integer heightCm;
    @TableField("education")
    private String education;
    @TableField("occupation")
    private String occupation;
    @TableField("income_range")
    private String incomeRange;
    @TableField("city")
    private String city;
    @TableField("wechat_id")
    private String wechatId;
    @TableField("douyin_id")
    private String douyinId;
    @TableField("douyin_nickname")
    private String douyinNickname;
    @TableField("douyin_profile_url")
    private String douyinProfileUrl;
    @TableField("status")
    private ProfileStatus status;
    @Version
    @TableField("version")
    private Long version;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
