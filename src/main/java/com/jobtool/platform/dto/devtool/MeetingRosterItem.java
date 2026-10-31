package com.jobtool.platform.dto.devtool;

import lombok.Data;

/**
 * 共创会人员参会情况统计 - 人员名单条目。
 *
 * <p>对应 {@code docs/meeting/meetingNameList.md} 中 JSON 数组的元素，样例：{"name":"罗乾勇","code":"20120148"}。</p>
 *
 * <p>code 必须声明为 String：名单里若写成不带引号的数字，前导零会丢失、
 * 大数字也可能被科学计数法化。</p>
 *
 * @author wangpengfei
 */
@Data
public class MeetingRosterItem {

    /** 姓名 */
    private String name;

    /** 工号，与 meeting_user_real_record.user_code 匹配 */
    private String code;
}
