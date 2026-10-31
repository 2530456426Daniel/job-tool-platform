package com.jobtool.platform.dto.devtool;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OKR达成情况统计 - 列表查询响应。
 *
 * @author wangpengfei
 */
@Data
public class OkrStatisticsListResponse {

    /** 是否已成功上传过数据；false 时 list 与 filters 为空，不作为错误处理 */
    private boolean uploaded;

    /** 最近一次上传成功时间 */
    private String uploadedAt;

    /** 最近上传的达成Excel文件名 */
    private String okrFileName;

    /** 人员名单路径 */
    private String nameListPath;

    /** 达成Excel行数 */
    private Integer okrTotal;

    /** 人员名单条数 */
    private Integer nameListTotal;

    /** 交集条数 */
    private Integer matchedTotal;

    /** 当前列表条数，等于 list.size() */
    private Integer total;

    /** 明细列表，元素为页面展示的 7 个字段 */
    private List<OkrStatisticsRecord> list = new ArrayList<>();

    /**
     * 筛选项，键与明细字段同名：
     * name、employeeNo、reviewRate、okrPublished、growthCurveUpdated、managerOrAbove、oAlignRate
     */
    private Map<String, List<OkrFilterOption>> filters = new LinkedHashMap<>();
}
