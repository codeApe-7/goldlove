package com.love.archive.admin.persistence;

import com.love.archive.admin.persistence.query.AdminActivationCodeRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminActivationCodeQueryMapper {

    @Select("""
            <script>
            SELECT c.id, c.code, c.bound_phone, c.granted_tier, c.status, c.note,
                   c.created_at, c.redeemed_at, c.redeemed_by_account_id,
                   r.phone AS redeemed_phone,
                   (b.id IS NOT NULL) AS bound_phone_registered
              FROM activation_code c
              LEFT JOIN user_account r ON r.id = c.redeemed_by_account_id
              LEFT JOIN user_account b ON b.phone = c.bound_phone
             WHERE 1 = 1
             <if test="phone != null">AND c.bound_phone LIKE #{phone}</if>
             <if test="status != null">AND c.status = #{status}</if>
             ORDER BY c.created_at DESC, c.id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminActivationCodeRow> search(
            @Param("phone") String phoneLike,
            @Param("status") String status,
            @Param("limit") long limit,
            @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM activation_code c
             WHERE 1 = 1
             <if test="phone != null">AND c.bound_phone LIKE #{phone}</if>
             <if test="status != null">AND c.status = #{status}</if>
            </script>
            """)
    long count(@Param("phone") String phoneLike, @Param("status") String status);
}
