package com.jobtool.platform.mapper.meeting;

import com.jobtool.platform.entity.meeting.MeetingActivity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 共创会活动 Mapper。
 *
 * <p>只用到按主键查询（回显共创会名称与活动日期），字段与属性通过
 * {@code map-underscore-to-camel-case} 自动映射。</p>
 *
 * @author wangpengfei
 */
public interface MeetingActivityMapper {

    /**
     * 按主键查询活动
     *
     * @param id 活动id
     * @return 活动；不存在时返回 null
     */
    @Select("""
            select id, meeting_name, meeting_city_code, meeting_city_name, meeting_begin_time, meeting_end_time
            from meeting_activity
            where id = #{id}
            """)
    MeetingActivity selectById(@Param("id") Long id);
}
