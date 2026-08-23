package com.love.archive.payment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentSettingMapper extends BaseMapper<PaymentSettingEntity> {

    /**
     * 写入唯一那一行。用 {@code ON CONFLICT} 一条语句完成「没有就插入、有就更新」，
     * 而不是先 select 再分支：两个管理员同时保存时，分支写法会有一个撞主键报错。
     *
     * <p>{@code adminId} 显式标了 {@code jdbcType}——它可以为 null（库级测试里没有管理员行），
     * 而 MyBatis 对没标类型的 null 会发一个未定型的参数，Postgres 未必推断得出来。</p>
     */
    @Insert("""
            INSERT INTO payment_setting (
                id, vip_upgrade_amount_minor, updated_by_admin_id, created_at, updated_at
            ) VALUES (1, #{amountMinor}, #{adminId,jdbcType=BIGINT}, #{now}, #{now})
            ON CONFLICT (id) DO UPDATE SET
                vip_upgrade_amount_minor = EXCLUDED.vip_upgrade_amount_minor,
                updated_by_admin_id = EXCLUDED.updated_by_admin_id,
                updated_at = EXCLUDED.updated_at
            """)
    int upsert(
            @Param("amountMinor") long amountMinor,
            @Param("adminId") Long adminId,
            @Param("now") OffsetDateTime now);
}
