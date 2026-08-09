package com.love.archive.guest.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProfileFieldDefinitionMapper extends BaseMapper<ProfileFieldDefinitionEntity> {

    @Select("""
            SELECT id, field_code, label, storage_kind, data_type, required, enabled,
                   ever_used, options_json, sort_order, instructions, version,
                   created_at, updated_at
            FROM profile_field_definition
            WHERE id = #{definitionId}
            FOR UPDATE
            """)
    ProfileFieldDefinitionEntity selectByIdForUpdate(@Param("definitionId") long definitionId);
}
