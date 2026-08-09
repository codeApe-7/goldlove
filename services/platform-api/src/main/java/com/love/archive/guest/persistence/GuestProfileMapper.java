package com.love.archive.guest.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GuestProfileMapper extends BaseMapper<GuestProfileEntity> {

    @Select("""
            SELECT id, user_account_id, gender, birth_date, height_cm, education,
                   occupation, income_range, city, wechat_id_ciphertext, wechat_id_hmac,
                   douyin_id_ciphertext, douyin_id_hmac, douyin_nickname_ciphertext,
                   douyin_profile_url_ciphertext, pending_revision_id,
                   current_approved_revision_id, status, version, created_at, updated_at
            FROM guest_profile
            WHERE user_account_id = #{accountId}
            FOR UPDATE
            """)
    GuestProfileEntity selectOwnedForUpdate(@Param("accountId") long accountId);
}
