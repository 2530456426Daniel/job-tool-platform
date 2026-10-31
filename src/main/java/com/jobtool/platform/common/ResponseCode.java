package com.jobtool.platform.common;

/**
 * 业务响应码。
 *
 * <p>约定：成功用 {@code 0}（见 {@link ApiResponse#CODE_SUCCESS}），失败码沿用原 devtool
 * 功能的编号段，前端只负责展示 {@code message}，不依赖具体数字。</p>
 *
 * @author wangpengfei
 */
public enum ResponseCode {

    /** 未预期的服务端异常 */
    SYSTEM_ERROR(900000, "系统错误"),

    /** 参数错误 */
    APPLICATION_PARAMETER_ERROR(800001, "参数错误"),

    /* ---------- devtool OKR达成情况统计 ---------- */

    OKR_EXCEL_PARSE_ERROR(220001, "解析达成情况 Excel 出错，请检查文件是否与导出模板一致"),
    OKR_EXCEL_MISSING_COLUMN(220002, "达成情况 Excel 缺少必要列：工号/姓名"),
    OKR_NAME_LIST_READ_ERROR(220003, "读取人员名单失败，请检查名单文件是否存在且格式正确"),
    OKR_NAME_LIST_EMPTY(220004, "人员名单为空，请检查名单内容"),
    OKR_FILE_TYPE_ERROR(220005, "上传文件格式不支持，请上传 .xlsx 或 .xls 文件"),
    OKR_NAME_LIST_ITEM_ERROR(220006, "人员名单存在缺少 name 或 code 字段的条目"),

    /* ---------- devtool 共创会人员参会情况统计 ---------- */

    MEETING_ACTIVITY_ID_INVALID(230001, "活动ID不能为空且必须大于0"),
    MEETING_ANSWER_SCORE_INVALID(230002, "答题分数必须大于0"),
    MEETING_ATTENDANCE_DURATION_INVALID(230003, "参会时长必须大于0"),
    MEETING_NAME_LIST_READ_ERROR(230004, "读取共创会人员名单失败，请检查名单文件是否存在且格式正确"),
    MEETING_NAME_LIST_EMPTY(230005, "共创会人员名单为空，请检查名单内容"),
    MEETING_NO_EXPORT_DATA(230006, "该活动下没有可导出的参会记录"),
    MEETING_IMPORT_EXCEL_PARSE_ERROR(230007, "解析共创会导入数据 Excel 出错，请检查文件是否与导入模板一致"),
    MEETING_IMPORT_EXCEL_MISSING_COLUMN(230008, "共创会导入数据 Excel 缺少必要列：activity_id/team_id/user_code/user_name"),
    MEETING_IMPORT_FILE_TYPE_ERROR(230009, "导入文件格式不支持，请上传 .xlsx 或 .xls 文件"),
    MEETING_IMPORT_EXCEL_EMPTY(230010, "共创会导入数据 Excel 中没有可导入的数据行"),
    MEETING_IMPORT_EXCEL_ROW_ERROR(230011, "共创会导入数据 Excel 存在不合法的数据行");

    private final int code;

    private final String message;

    ResponseCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
