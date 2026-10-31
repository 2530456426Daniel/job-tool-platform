package com.jobtool.platform.controller.devtool;

import com.jobtool.platform.common.ApiResponse;
import com.jobtool.platform.common.BusinessException;
import com.jobtool.platform.common.ResponseCode;
import com.jobtool.platform.dto.devtool.MeetingUserCollectionListResponse;
import com.jobtool.platform.dto.devtool.MeetingUserCollectionRequest;
import com.jobtool.platform.dto.devtool.MeetingUserRecordImportResponse;
import com.jobtool.platform.service.devtool.MeetingUserCollectionService;
import com.jobtool.platform.support.devtool.JsonSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 共创会人员参会情况统计接口。
 *
 * <p>查询与导入返回统一 JSON；导出直接返回 Excel 字节流，
 * 失败时（参数非法/名单读取失败/无可导出数据）仍返回统一 JSON 错误体，便于前端提示。</p>
 *
 * @author wangpengfei
 */
@Slf4j
@Tag(name = "devtool - 共创会人员统计", description = "导入参会数据、查询明细与战队汇总、导出 Excel")
@RestController
@RequestMapping("/devtool/meeting/user/collection")
public class MeetingUserCollectionController {

    /** Excel 响应类型 */
    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 导出文件名时间戳格式 */
    private static final String FILE_NAME_TIME_PATTERN = "yyyyMMddHHmmss";

    private final MeetingUserCollectionService meetingUserCollectionService;

    public MeetingUserCollectionController(MeetingUserCollectionService meetingUserCollectionService) {
        this.meetingUserCollectionService = meetingUserCollectionService;
    }

    /**
     * 导入参会数据（导入模版Excel）
     *
     * <p>导入前会按 Excel 中出现的「活动ID + 战队ID」组合清空原有数据，再批量插入；
     * 清空与插入在同一个事务内完成，任一步失败整体回滚。</p>
     *
     * @param file 导入模版Excel（.xlsx/.xls）
     * @return 导入结果摘要：解析行数、清空条数、插入条数、分组明细
     */
    @Operation(summary = "导入共创会参会数据",
            description = "multipart/form-data，参数名 file；按活动ID+战队ID先清空再插入，同一事务内完成")
    @PostMapping("/v1/importMeetingUserRealRecordExcel")
    public ApiResponse<MeetingUserRecordImportResponse> importMeetingUserRealRecordExcel(
            @RequestParam(value = "file", required = false) MultipartFile file) {
        log.info("共创会数据导入-上传，fileName={}", file == null ? null : file.getOriginalFilename());
        return ApiResponse.ok(meetingUserCollectionService.importExcel(file));
    }

    /**
     * 查询共创会参会明细与战队汇总
     *
     * @param request 活动ID、答题分数（满分）、参会时长（秒）
     * @return 明细、汇总与统计摘要
     */
    @Operation(summary = "查询共创会参会明细", description = "POST，请求体 {activityId, answerScore, attendanceDuration}")
    @PostMapping("/v1/getMeetingUserCollectionList")
    public ApiResponse<MeetingUserCollectionListResponse> getMeetingUserCollectionList(
            @RequestBody(required = false) MeetingUserCollectionRequest request) {
        log.info("共创会人员统计-查询，activityId={}，answerScore={}，attendanceDuration={}",
                request == null ? null : request.getActivityId(),
                request == null ? null : request.getAnswerScore(),
                request == null ? null : request.getAttendanceDuration());
        return ApiResponse.ok(meetingUserCollectionService.list(request));
    }

    /**
     * 导出与样例模板一致的Excel
     *
     * <p>先把工作簿写入内存缓冲，全部成功后才写响应头与响应体；
     * 这样参数非法、名单读取失败、无可导出数据时仍能返回统一的 JSON 错误体。</p>
     */
    @Operation(summary = "导出共创会人员统计Excel", description = "POST，请求体同查询接口；成功返回 xlsx 字节流，失败返回 JSON 错误体")
    @PostMapping("/v1/exportMeetingUserCollectionExcel")
    public void exportMeetingUserCollectionExcel(@RequestBody(required = false) MeetingUserCollectionRequest request,
                                                 HttpServletResponse response) {
        log.info("共创会人员统计-导出请求，activityId={}，answerScore={}，attendanceDuration={}",
                request == null ? null : request.getActivityId(),
                request == null ? null : request.getAnswerScore(),
                request == null ? null : request.getAttendanceDuration());
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            meetingUserCollectionService.export(request, buffer);

            response.setContentType(EXCEL_CONTENT_TYPE);
            response.setHeader("Content-Disposition",
                    "attachment;filename=" + URLEncoder.encode(buildFileName(request), StandardCharsets.UTF_8));
            response.setContentLength(buffer.size());
            buffer.writeTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (BusinessException e) {
            log.warn("共创会人员统计-导出失败，code={}，message={}", e.getCode(), e.getMessage());
            writeJsonError(response, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("共创会人员统计-导出异常", e);
            writeJsonError(response, ResponseCode.SYSTEM_ERROR.getCode(), ResponseCode.SYSTEM_ERROR.getMessage());
        }
    }

    /**
     * 导出文件名：共创会人员统计_{活动ID}_{时间戳}.xlsx
     */
    private String buildFileName(MeetingUserCollectionRequest request) {
        Long activityId = request == null || request.getActivityId() == null ? 0L : request.getActivityId();
        String timestamp = new SimpleDateFormat(FILE_NAME_TIME_PATTERN).format(new Date());
        return "共创会人员统计_" + activityId + "_" + timestamp + ".xlsx";
    }

    /**
     * 导出失败时以 JSON 返回错误（此时尚未写出任何 Excel 字节）
     */
    private void writeJsonError(HttpServletResponse response, int code, String message) {
        if (response.isCommitted()) {
            log.warn("响应已提交，无法返回导出错误体，code={}，message={}", code, message);
            return;
        }
        try {
            response.reset();
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JsonSupport.write(ApiResponse.fail(code, message)));
            response.getWriter().flush();
        } catch (IOException e) {
            log.error("写出导出错误响应失败", e);
        }
    }
}
