package com.jobtool.platform.service.devtool;

import com.jobtool.platform.dto.devtool.OkrStatisticsListResponse;
import com.jobtool.platform.dto.devtool.OkrStatisticsUploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * OKR达成情况统计 Service。
 *
 * @author wangpengfei
 */
public interface OkrStatisticsService {

    /**
     * 上传达成情况Excel：解析 -> 重新读取人员名单 -> 取交集 -> 整体替换内存数据
     *
     * @param file 达成情况Excel
     * @return 上传结果摘要
     */
    OkrStatisticsUploadResponse upload(MultipartFile file);

    /**
     * 读取当前内存数据，返回明细列表与筛选项
     *
     * @return 列表响应
     */
    OkrStatisticsListResponse list();

    /**
     * 清空内存数据
     */
    void clear();
}
