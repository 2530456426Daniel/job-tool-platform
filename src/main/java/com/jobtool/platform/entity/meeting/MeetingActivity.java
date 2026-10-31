package com.jobtool.platform.entity.meeting;

import lombok.Data;

import java.util.Date;

/**
 * 共创会活动。
 *
 * <p>表：{@code meeting_activity}。本功能只用于回显与导出模板右上角的
 * 「共创会名称 / 共创会ID / 活动日期」，因此只声明需要的字段。</p>
 *
 * @author wangpengfei
 */
@Data
public class MeetingActivity {

    /** 活动id */
    private Long id;

    /** 活动名称 */
    private String meetingName;

    /** 活动城市 */
    private String meetingCityCode;

    /** 活动城市名称 */
    private String meetingCityName;

    /** 活动开始时间（导出模板的「活动日期」取该字段的日期部分） */
    private Date meetingBeginTime;

    /** 活动结束时间 */
    private Date meetingEndTime;
}
