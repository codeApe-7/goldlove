package com.love.archive.course.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.love.archive.course.persistence.query.CourseCollectionCountRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseMapper extends BaseMapper<CourseEntity> {

    /**
     * 按合集统计课程数。合集数量是个位数，所以一次全量取回再在内存里配对，
     * 比让列表接口对每个合集各查一次（N+1）划算。
     */
    @Select("""
            SELECT collection_id,
                   COUNT(*) AS course_count,
                   COUNT(*) FILTER (WHERE status = 'PUBLISHED') AS published_course_count
            FROM course
            GROUP BY collection_id
            """)
    List<CourseCollectionCountRow> countByCollection();
}
