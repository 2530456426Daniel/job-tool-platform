package com.jobtool.platform.dto.devtool;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 共创会人员参会情况统计 - 查询响应。
 *
 * @author wangpengfei
 */
@Data
public class MeetingUserCollectionListResponse {

    /** 活动id（回显入参） */
    private Long activityId;

    /** 共创会名称（meeting_activity.meeting_name），取不到为 null */
    private String meetingName;

    /** 活动日期 yyyy-MM-dd（meeting_activity.meeting_begin_time 的日期部分），取不到为 null */
    private String meetingDate;

    /** 答题分数（回显入参：该活动答题满分） */
    private Double answerScore;

    /** 参会时长（回显入参：应观看时长，秒） */
    private Long attendanceDuration;

    /** 人员名单实际来源，如 classpath:docs/meeting/meetingNameList.md */
    private String nameListPath;

    /** 人员名单条数 */
    private Integer nameListTotal;

    /** 该活动的记录数（不含名单过滤） */
    private Integer recordTotal;

    /** 名单与记录的匹配条数，等于 list.size() */
    private Integer matchedTotal;

    /** 记录中因不在名单被丢弃的人数 */
    private Integer excludedNotInNameList;

    /** 名单中该活动无记录的人数（不展示，仅计数） */
    private Integer excludedNoRecord;

    /** 汇总战队名（明细第一条记录的 team_name），无明细时为 null */
    private String teamName;

    /** 当前明细条数 */
    private Integer total;

    /** 明细列表 */
    private List<MeetingCollectionRecord> list = new ArrayList<>();

    /** 战队汇总；无明细时为 null */
    private MeetingCollectionSummary summary;
}
