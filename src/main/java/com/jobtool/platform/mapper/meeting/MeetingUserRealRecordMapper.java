package com.jobtool.platform.mapper.meeting;

import com.jobtool.platform.dto.devtool.MeetingCollectionSummary;
import com.jobtool.platform.entity.meeting.MeetingUserRealRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 共创会人员真实记录 Mapper。
 *
 * <p>SQL 见 {@code resources/mapper/meeting/MeetingUserRealRecordMapper.xml}。</p>
 *
 * @author wangpengfei
 */
public interface MeetingUserRealRecordMapper {

    /**
     * 按活动id查询参会记录（按 id 升序，保证明细顺序确定）。
     *
     * <p>只按活动id过滤，人员名单的交集在 Service 层完成。</p>
     *
     * @param activityId 活动id
     * @return 该活动的全部参会记录
     */
    List<MeetingUserRealRecord> selectByActivityId(@Param("activityId") Long activityId);

    /**
     * 查询战队维度的汇总（不过人员名单过滤）
     *
     * @param activityId         活动id
     * @param teamName           战队名，取自明细第一条记录的 team_name
     * @param attendanceDuration 参会时长（应观看时长，秒），观看时长封顶值与观看率分母
     * @param answerScore        答题分数（该活动答题满分），答题准确率分母
     * @return 汇总；该战队无记录时返回 null
     */
    MeetingCollectionSummary selectTeamSummary(@Param("activityId") Long activityId,
                                               @Param("teamName") String teamName,
                                               @Param("attendanceDuration") Long attendanceDuration,
                                               @Param("answerScore") Double answerScore);

    /**
     * 按「活动ID + 战队ID」清空原有参会记录（导入数据前调用）。
     *
     * <p>刻意不加 {@code <if>} 动态条件：teamId 为空时 {@code team_id = null} 匹配不到任何行，
     * 不会误删整个活动的数据。</p>
     *
     * @param activityId 活动id
     * @param teamId     战队id
     * @return 删除条数
     */
    int deleteByActivityIdAndTeamId(@Param("activityId") Long activityId, @Param("teamId") Long teamId);

    /**
     * 批量插入参会记录（id 由数据库自增，按列表顺序落库）
     *
     * @param list 参会记录，不能为空
     * @return 插入条数
     */
    int batchInsert(@Param("list") List<MeetingUserRealRecord> list);
}
