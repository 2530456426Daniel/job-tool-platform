package com.jobtool.platform.constants;

/**
 * OKR达成情况统计 常量。
 *
 * <p>这些值是页面筛选与展示约定的一部分，前端按字面量比对（如 {@code reviewRate === '100%'}），
 * 不要随意改动。</p>
 *
 * @author wangpengfei
 */
public final class OkrStatisticsConstant {

    /** O对齐率源值为空时的展示值。源值为空意味着本月未及时发布，用于与“未对齐0%”区分 */
    public static final String O_ALIGN_NOT_APPLICABLE = "不适用";

    /** 源数据缺失时的展示值，例如导出模板缺少该列 */
    public static final String UNKNOWN_VALUE = "未知";

    public static final String YES = "是";

    public static final String NO = "否";

    public static final String DONE = "完成";

    public static final String UNDONE = "未完成";

    private OkrStatisticsConstant() {
    }
}
