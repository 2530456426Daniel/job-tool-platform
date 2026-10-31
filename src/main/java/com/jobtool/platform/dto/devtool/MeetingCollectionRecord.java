package com.jobtool.platform.dto.devtool;

import lombok.Data;

/**
 * 共创会人员参会情况统计 - 明细行。
 *
 * <p>字段与导出模板 A–I 列一一对应（列序即字段顺序），计算口径：</p>
 * <pre>
 * D 观看时长 = MIN(watch_duration, 参会时长)
 * E 参会率   = join_flag * 100
 * F 答题率   = answer_flag * 100
 * G 答题准确率 = ROUND(answer_score_total * 100 / 答题分数, 1)
 * H 观看率   = ROUND(D * 100 / 参会时长, 1)
 * I 分数     = ROUND((E + F + G + H) / 4, 1)
 * </pre>
 *
 * @author wangpengfei
 */
@Data
public class MeetingCollectionRecord {

    /** A 用户编号（字符串，避免前导零丢失与科学计数法） */
    private String userCode;

    /** B 姓名 */
    private String userName;

    /** 战队名（接口返回但不展示，汇总行与排查用） */
    private String teamName;

    /** C 答题分数：answer_score_total 原值 */
    private Double answerScoreTotal;

    /** D 观看时长（单位秒）：已按参会时长封顶 */
    private Long watchDuration;

    /** E 参会率 */
    private Double joinRate;

    /** F 答题率 */
    private Double answerRate;

    /** G 答题准确率 */
    private Double answerAccuracy;

    /** H 观看率 */
    private Double watchRate;

    /** I 分数：4 个率的算术平均 */
    private Double score;
}
