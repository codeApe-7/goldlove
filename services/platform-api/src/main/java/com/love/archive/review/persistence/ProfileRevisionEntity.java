package com.love.archive.review.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.love.archive.review.domain.RevisionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("profile_revision")
public class ProfileRevisionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("guest_profile_id")
    private Long guestProfileId;
    @TableField("revision_number")
    private Integer revisionNumber;
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
    @TableField("status")
    private RevisionStatus status;
    @TableField("submitted_by_account_id")
    private Long submittedByAccountId;
    @TableField("submitted_at")
    private OffsetDateTime submittedAt;
    @TableField("review_deadline_at")
    private OffsetDateTime reviewDeadlineAt;
    @TableField("reviewed_at")
    private OffsetDateTime reviewedAt;
    @TableField("submission_key_hmac")
    private String submissionKeyHmac;
    @TableField("request_payload_sha256")
    private String requestPayloadSha256;
    @Version
    @TableField("version")
    private Long version;
    @TableField("created_at")
    private OffsetDateTime createdAt;

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
