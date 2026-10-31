package com.jobtool.platform.entity.meeting;

import lombok.Data;

import java.util.Date;

/**
 * 共创会人员真实记录。
 *
 * <p>表：{@code meeting_user_real_record}，与表结构一一对应。</p>
 *
 * @author wangpengfei
 */
@Data
public class MeetingUserRealRecord {

    /** 主键 */
    private Long id;

    /** 活动id */
    private Long activityId;

    /** 战队id */
    private Long teamId;

    /** 战队名 */
    private String teamName;

    /** 城市编码 */
    private String cityCode;

    /** 城市名称 */
    private String cityName;

    /** 员工所属一级部门code */
    private String deptLevelOne;

    /** 员工所属二级部门code */
    private String deptLevelTwo;

    /** 员工所属三级部门code */
    private String deptLevelThree;

    /** 员工所属四级部门code */
    private String deptLevelFour;

    /** 员工所属五级部门code */
    private String deptLevelFive;

    /** 员工所属六级部门code */
    private String deptLevelSix;

    /** 员工所属七级部门code */
    private String deptLevelSeven;

    /** 员工编号 */
    private Long userCode;

    /** 员工名称 */
    private String userName;

    /** 是否参会(0-否，1-是) */
    private Integer joinFlag;

    /** 参会时间 */
    private Date joinTime;

    /** 是否迟到(0-否，1-是) */
    private Integer joinLateFlag;

    /** 观看直播时长，单位为秒 */
    private Long watchDuration;

    /** 是否答题(0-否，1-是) */
    private Integer answerFlag;

    /** 答题总分数 */
    private Double answerScoreTotal;

    /** 创建时间 */
    private Date createTime;
}
