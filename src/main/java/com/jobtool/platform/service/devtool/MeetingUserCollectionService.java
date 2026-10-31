package com.jobtool.platform.service.devtool;

import com.jobtool.platform.dto.devtool.MeetingUserCollectionListResponse;
import com.jobtool.platform.dto.devtool.MeetingUserCollectionRequest;
import com.jobtool.platform.dto.devtool.MeetingUserRecordImportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 共创会人员参会情况统计 Service。
 *
 * @author wangpengfei
 */
public interface MeetingUserCollectionService {

    /**
     * 按 活动ID + 答题分数（满分） + 参会时长（应观看时长） 查询参会明细与战队汇总
     *
     * @param request 查询入参
     * @return 明细列表、战队汇总与统计摘要
     */
    MeetingUserCollectionListResponse list(MeetingUserCollectionRequest request);

    /**
     * 导出与样例模板一致的Excel
     *
     * @param request      查询入参（与查询接口一致）
     * @param outputStream 输出流
     * @throws IOException             写出失败
     * @throws com.jobtool.platform.common.BusinessException 参数非法/名单读取失败/无可导出数据
     */
    void export(MeetingUserCollectionRequest request, OutputStream outputStream) throws IOException;

    /**
     * 导入参会数据：解析导入模版Excel，按「活动ID + 战队ID」先清空原有数据，再批量插入。
     *
     * <p>整个导入在一个事务内完成：任何一步失败都会回滚，不会出现「清空了但没导入」的中间态。</p>
     *
     * @param file 导入模版Excel（.xlsx/.xls）
     * @return 导入结果摘要（清空条数、插入条数、按活动+战队分组明细）
     */
    MeetingUserRecordImportResponse importExcel(MultipartFile file);
}
