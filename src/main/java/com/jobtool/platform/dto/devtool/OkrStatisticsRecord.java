package com.jobtool.platform.dto.devtool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * OKR达成情况统计 - 单条统计记录。
 *
 * <p>既是内存中保存的数据，也是列表接口返回的元素，字段严格对应页面展示的 7 列，
 * 不额外携带接口不需要的字段。</p>
 *
 * @author wangpengfei
 */
@Data
public class OkrStatisticsRecord {

    /** 姓名（达成Excel C列） */
    private String name;

    /** 工号（达成Excel D列），同时作为内存Map的键 */
    private String employeeNo;

    /** 上月OKR达成复盘率（达成Excel E列），原样保存，如 100% */
    private String reviewRate;

    /** 本月OKRI是否及时发布（达成Excel J列），取值 是/否 */
    private String okrPublished;

    /** 增长曲线是否更新完成（达成Excel M列），取值 完成/未完成 */
    private String growthCurveUpdated;

    /** 是否是经理及以上（达成Excel P列），取值 是/否 */
    private String managerOrAbove;

    /**
     * O对齐率（达成Excel R列）；源值为空时由服务层归一化为“不适用”。
     *
     * <p><b>字段名不要改回 oAlignRate</b>：Lombok 会生成 getOAlignRate()，
     * 而 Jackson 对 getter 的默认命名规则会把“get 之后连续的大写字母”全部小写化，
     * 于是对外字段名会变成 {@code oalignRate}，前端按 {@code oAlignRate} 取就永远是 undefined。</p>
     *
     * <p>这里用 alignRate 让字段与 getter 的隐式名一致（不会产生重复字段），
     * 再用 {@link JsonProperty} 把对外字段名固定为 oAlignRate。</p>
     */
    @JsonProperty("oAlignRate")
    private String alignRate;
}
