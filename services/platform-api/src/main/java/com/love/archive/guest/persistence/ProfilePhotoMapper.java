package com.love.archive.guest.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.love.archive.guest.domain.PhotoCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProfilePhotoMapper extends BaseMapper<ProfilePhotoEntity> {

    @Select("""
            SELECT COALESCE(MAX(sort_order), -1) + 1
            FROM profile_photo
            WHERE guest_profile_id = #{guestProfileId}
              AND category = #{category}
            """)
    int selectMaxSortOrder(
            @Param("guestProfileId") long guestProfileId,
            @Param("category") PhotoCategory category);
}
