package com.jobtool.platform.dto.devtool;

import lombok.Data;

/**
 * OKR达成情况统计 - 上传结果摘要。
 *
 * @author wangpengfei
 */
@Data
public class OkrStatisticsUploadResponse {

    /** 上传成功时间 yyyy-MM-dd HH:mm:ss */
    private String uploadedAt;

    /** 上传的达成Excel文件名 */
    private String okrFileName;

    /** 实际读取到的人员名单路径 */
    private String nameListPath;

    /** 达成Excel解析行数 */
    private Integer okrTotal;

    /** 人员名单条数 */
    private Integer nameListTotal;

    /** 交集条数，即当前内存中的数据量 */
    private Integer matchedTotal;

    /** 因不在人员名单中而被丢弃的人数 */
    private Integer excludedNotInNameList;

    /** 在人员名单中但达成Excel里没有的人数 */
    private Integer excludedNotInOkr;
}
