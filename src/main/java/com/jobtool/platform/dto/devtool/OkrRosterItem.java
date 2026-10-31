package com.jobtool.platform.dto.devtool;

import lombok.Data;

/**
 * OKR达成情况统计 - 人员名单条目。
 *
 * <p>对应 {@code docs/okr/okrNameList.md} 中 JSON 数组的元素，样例：{"name":"张琴","code":"20212415"}。</p>
 *
 * <p>code 必须声明为 String：名单里若写成不带引号的数字，前导零会丢失、
 * 大数字也可能被科学计数法化。</p>
 *
 * @author wangpengfei
 */
@Data
public class OkrRosterItem {

    /** 姓名 */
    private String name;

    /** 工号，匹配主键 */
    private String code;
}
