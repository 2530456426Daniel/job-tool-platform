package com.jobtool.platform.dto.devtool;

import lombok.Data;

/**
 * 共创会参会数据导入 - 按「活动ID + 战队ID」分组的导入结果。
 *
 * <p>导入前会先按该组合清空原有数据，因此每组都记录清空条数与导入条数。</p>
 *
 * @author wangpengfei
 */
@Data
public class MeetingUserRecordImportGroup {

    /** 活动ID */
    private Long activityId;

    /** 战队ID */
    private Long teamId;

    /** 战队名（取自导入数据的第一行，仅用于结果展示） */
    private String teamName;

    /** 本次导入的行数 */
    private Integer rowCount;

    /** 清空的原有条数 */
    private Integer deletedCount;
}
