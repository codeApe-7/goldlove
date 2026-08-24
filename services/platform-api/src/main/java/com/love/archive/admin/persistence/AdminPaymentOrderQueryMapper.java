package com.love.archive.admin.persistence;

import com.love.archive.admin.persistence.query.AdminPaymentOrderRow;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 分页用显式 LIMIT/OFFSET，不依赖 MyBatis-Plus 的分页拦截器——
 * 自定义 {@code @Select} 上的 IPage 参数并不会被它加上边界，静默返回全表。
 */
@Mapper
public interface AdminPaymentOrderQueryMapper {

    @Select("""
            <script>
            SELECT o.id, o.out_trade_no, o.channel, o.amount_minor, o.status,
                   o.channel_trade_no, o.paid_at, o.created_at,
                   a.id AS user_account_id, a.phone
              FROM payment_order o
              JOIN user_account a ON a.id = o.user_account_id
             WHERE 1 = 1
             <if test="phone != null">AND a.phone LIKE #{phone}</if>
             <if test="status != null">AND o.status = #{status}</if>
             ORDER BY o.created_at DESC, o.id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminPaymentOrderRow> search(
            @Param("phone") String phoneLike,
            @Param("status") String status,
            @Param("limit") long limit,
            @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM payment_order o
              JOIN user_account a ON a.id = o.user_account_id
             WHERE 1 = 1
             <if test="phone != null">AND a.phone LIKE #{phone}</if>
             <if test="status != null">AND o.status = #{status}</if>
            </script>
            """)
    long count(@Param("phone") String phoneLike, @Param("status") String status);

    @Select("""
            SELECT COALESCE(SUM(amount_minor), 0)
              FROM payment_record
             WHERE status = 'PAID' AND paid_at >= #{dayStart} AND paid_at < #{dayEnd}
            """)
    long paidAmountBetween(
            @Param("dayStart") OffsetDateTime dayStart,
            @Param("dayEnd") OffsetDateTime dayEnd);
}
