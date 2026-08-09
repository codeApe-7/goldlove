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
    @TableField("wechat_id_ciphertext")
    private byte[] wechatIdCiphertext;
    @TableField("wechat_id_hmac")
    private String wechatIdHmac;
    @TableField("douyin_id_ciphertext")
    private byte[] douyinIdCiphertext;
    @TableField("douyin_id_hmac")
    private String douyinIdHmac;
    @TableField("douyin_nickname_ciphertext")
    private byte[] douyinNicknameCiphertext;
    @TableField("douyin_profile_url_ciphertext")
    private byte[] douyinProfileUrlCiphertext;
    @TableField("pending_revision_id")
    private Long pendingRevisionId;
    @TableField("current_approved_revision_id")
    private Long currentApprovedRevisionId;
    @TableField("status")
    private ProfileStatus status;
    @Version
    @TableField("version")
    private Long version;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    public byte[] getWechatIdCiphertext() { return cloneOrNull(wechatIdCiphertext); }
    public void setWechatIdCiphertext(byte[] wechatIdCiphertext) { this.wechatIdCiphertext = cloneOrNull(wechatIdCiphertext); }
    public byte[] getDouyinIdCiphertext() { return cloneOrNull(douyinIdCiphertext); }
    public void setDouyinIdCiphertext(byte[] douyinIdCiphertext) { this.douyinIdCiphertext = cloneOrNull(douyinIdCiphertext); }
    public byte[] getDouyinNicknameCiphertext() { return cloneOrNull(douyinNicknameCiphertext); }
    public void setDouyinNicknameCiphertext(byte[] douyinNicknameCiphertext) { this.douyinNicknameCiphertext = cloneOrNull(douyinNicknameCiphertext); }
    public byte[] getDouyinProfileUrlCiphertext() { return cloneOrNull(douyinProfileUrlCiphertext); }
    public void setDouyinProfileUrlCiphertext(byte[] douyinProfileUrlCiphertext) { this.douyinProfileUrlCiphertext = cloneOrNull(douyinProfileUrlCiphertext); }

    private static byte[] cloneOrNull(byte[] value) {
        return value == null ? null : value.clone();
    }
}
