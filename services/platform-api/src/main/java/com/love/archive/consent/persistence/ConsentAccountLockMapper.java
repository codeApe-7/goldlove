package com.love.archive.consent.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConsentAccountLockMapper {

    @Select("""
            SELECT id
            FROM user_account
            WHERE id = #{accountId}
            FOR UPDATE
            """)
    Long lockAccount(@Param("accountId") long accountId);
}
