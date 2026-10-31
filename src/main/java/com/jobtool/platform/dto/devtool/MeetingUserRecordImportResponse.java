package com.jobtool.platform.dto.devtool;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 共创会参会数据导入 - 响应。
 *
 * @author wangpengfei
 */
@Data
public class MeetingUserRecordImportResponse {

    /** 上传的文件名 */
    private String fileName;

    /** 导入完成时间 yyyy-MM-dd HH:mm:ss */
    private String importedAt;

    /** 解析出的数据行数（= insertedTotal） */
    private Integer total;

    /** 清空的原有条数（按 Excel 中出现的 活动ID+战队ID 组合分别清空） */
    private Integer deletedTotal;

    /** 本次插入的条数 */
    private Integer insertedTotal;

    /** 按 活动ID + 战队ID 分组的明细 */
    private List<MeetingUserRecordImportGroup> groups = new ArrayList<>();
}
