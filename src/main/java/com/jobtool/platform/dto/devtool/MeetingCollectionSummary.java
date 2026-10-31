package com.jobtool.platform.dto.devtool;

import lombok.Data;

/**
 * 共创会人员参会情况统计 - 战队汇总行。
 *
 * <p>对应导出模板第 55–56 行的汇总块（表头：用户编号 / 总人数 / 答题平均分数 /
 * 观看平均时长（单位秒） / 参会率 / 答题率 / 答题准确率 / 观看率 / 分数）。</p>
 *
 * <p>本类同时作为 Mapper 汇总查询的结果对象：SQL 已按 1 位小数四舍五入，
 * {@code score} 由 Service 用汇总行自己的 4 个率再算一次（与样例模板一致）。</p>
 *
 * @author wangpengfei
 */
@Data
public class MeetingCollectionSummary {

    /** A 战队名（取自明细第一条记录的 team_name，模板中该列表头为「用户编号」） */
    private String teamName;

    /** B 总人数：整个战队的人数，不过人员名单过滤 */
    private Integer totalCount;

    /** C 答题平均分数 */
    private Double avgAnswerScore;

    /** D 观看平均时长（单位秒），已按参会时长封顶 */
    private Double avgWatchDuration;

    /** E 参会率 */
    private Double joinRate;

    /** F 答题率 */
    private Double answerRate;

    /** G 答题准确率 */
    private Double answerAccuracy;

    /** H 观看率 */
    private Double watchRate;

    /** I 分数：ROUND((E + F + G + H) / 4, 1) */
    private Double score;
}
