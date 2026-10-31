package com.jobtool.platform.dto.devtool;

import lombok.Data;

/**
 * 共创会人员参会情况统计 - 查询入参。
 *
 * <p>三个参数都必填：</p>
 * <ul>
 *     <li>{@code activityId}：活动id</li>
 *     <li>{@code answerScore}：<b>该活动的答题满分（分母）</b>，不是某个人的得分；
 *         个人的答题总得分是 {@code meeting_user_real_record.answer_score_total}</li>
 *     <li>{@code attendanceDuration}：<b>该活动的应观看时长（秒）</b>，
 *         既是「观看时长」的封顶值，也是「观看率」的分母</li>
 * </ul>
 *
 * @author wangpengfei
 */
@Data
public class MeetingUserCollectionRequest {

    /** 活动id */
    private Long activityId;

    /** 答题分数：该活动的答题满分（分母），需大于0 */
    private Double answerScore;

    /** 参会时长：应观看时长，单位秒（封顶值与分母），需大于0 */
    private Long attendanceDuration;
}
