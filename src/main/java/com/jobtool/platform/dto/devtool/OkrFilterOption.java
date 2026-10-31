package com.jobtool.platform.dto.devtool;

import lombok.Data;

/**
 * OKR达成情况统计 - 单个筛选项。
 *
 * <p>同一字段下取值相同的记录合并为一项，count 为人数；
 * label 为页面直接渲染的文案，格式为“值（个数）”，如 100%（2）。</p>
 *
 * @author wangpengfei
 */
@Data
public class OkrFilterOption {

    /** 用于筛选比对的原始值 */
    private String value;

    /** 展示文案，格式：值（个数） */
    private String label;

    /** 该取值对应的人数 */
    private Integer count;

    public OkrFilterOption() {
    }

    public OkrFilterOption(String value, Integer count) {
        this.value = value;
        this.count = count;
        this.label = (value == null ? "" : value) + "（" + count + "）";
    }
}
