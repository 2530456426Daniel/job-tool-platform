package com.jobtool.platform.controller.devtool;

import com.jobtool.platform.common.ApiResponse;
import com.jobtool.platform.dto.devtool.OkrStatisticsListResponse;
import com.jobtool.platform.dto.devtool.OkrStatisticsUploadResponse;
import com.jobtool.platform.service.devtool.OkrStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * OKR达成情况统计接口。
 *
 * <p>业务异常统一由 {@link com.jobtool.platform.common.GlobalExceptionHandler} 转换为
 * {@code ApiResponse(code, message, null)}，Controller 只负责请求与响应。</p>
 *
 * @author wangpengfei
 */
@Slf4j
@Tag(name = "devtool - OKR达成情况统计", description = "上传达成情况 Excel、查询明细与筛选项、清空内存数据")
@RestController
@RequestMapping("/devtool/okr/statistics")
public class OkrStatisticsController {

    private final OkrStatisticsService okrStatisticsService;

    public OkrStatisticsController(OkrStatisticsService okrStatisticsService) {
        this.okrStatisticsService = okrStatisticsService;
    }

    /**
     * 上传达成情况Excel：解析并与人员名单取交集后，整体替换内存数据
     *
     * @param file 达成情况Excel（.xlsx/.xls）
     * @return 上传结果摘要：达成行数、名单条数、匹配条数、过滤条数
     */
    @Operation(summary = "上传达成情况Excel",
            description = "multipart/form-data，参数名 file；上传后会重新读取人员名单并取交集，整体替换内存数据")
    @PostMapping("/v1/uploadOkrStatisticsExcel")
    public ApiResponse<OkrStatisticsUploadResponse> uploadOkrStatisticsExcel(
            @RequestParam(value = "file", required = false) MultipartFile file) {
        log.info("OKR统计-上传达成Excel，fileName={}", file == null ? null : file.getOriginalFilename());
        return ApiResponse.ok(okrStatisticsService.upload(file));
    }

    /**
     * 查询当前内存中的明细列表与筛选项
     *
     * <p>未上传过数据时返回 uploaded=false 与空列表，不作为错误处理。</p>
     */
    @Operation(summary = "查询OKR统计列表", description = "POST，无请求体；返回明细、筛选项与上传摘要")
    @PostMapping("/v1/getOkrStatisticsList")
    public ApiResponse<OkrStatisticsListResponse> getOkrStatisticsList() {
        return ApiResponse.ok(okrStatisticsService.list());
    }

    /**
     * 清空内存数据（调试用），便于重新上传
     */
    @Operation(summary = "清空OKR统计数据", description = "POST，无请求体；清空内存中的明细与上传摘要")
    @PostMapping("/v1/clearOkrStatisticsData")
    public ApiResponse<Boolean> clearOkrStatisticsData() {
        okrStatisticsService.clear();
        return ApiResponse.ok(true);
    }
}
