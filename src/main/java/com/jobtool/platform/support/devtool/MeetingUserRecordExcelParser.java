package com.jobtool.platform.support.devtool;

import com.jobtool.platform.common.BusinessException;
import com.jobtool.platform.common.ResponseCode;
import com.jobtool.platform.entity.meeting.MeetingUserRealRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 共创会参会数据导入 - 导入模版Excel解析工具。
 *
 * <p>模版 21 列与 {@code meeting_user_real_record} 的字段一一对应（不含自增主键 id）。解析约定：</p>
 * <ul>
 *     <li>取第一个 sheet，不硬编码 sheet 名</li>
 *     <li>表头在第 1 行，数据从第 2 行开始；全空行自动跳过</li>
 *     <li>表头先归一化再匹配：去掉空白（含全角空格）、反引号与引号，并去掉导出 SQL 带出的 {@code +''} 尾巴，
 *         因此 {@code `user_code`+''} 与 {@code user_code} 都能识别</li>
 *     <li>数值按 {@link BigDecimal} 原样取值，100.5 这类答题分数不会被格式化成整数</li>
 * </ul>
 *
 * @author wangpengfei
 */
public final class MeetingUserRecordExcelParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeetingUserRecordExcelParser.class);

    private static final String COL_ACTIVITY_ID = "activity_id";
    private static final String COL_TEAM_ID = "team_id";
    private static final String COL_TEAM_NAME = "team_name";
    private static final String COL_CITY_CODE = "city_code";
    private static final String COL_CITY_NAME = "city_name";
    private static final String COL_DEPT_LEVEL_ONE = "dept_level_one";
    private static final String COL_DEPT_LEVEL_TWO = "dept_level_two";
    private static final String COL_DEPT_LEVEL_THREE = "dept_level_three";
    private static final String COL_DEPT_LEVEL_FOUR = "dept_level_four";
    private static final String COL_DEPT_LEVEL_FIVE = "dept_level_five";
    private static final String COL_DEPT_LEVEL_SIX = "dept_level_six";
    private static final String COL_DEPT_LEVEL_SEVEN = "dept_level_seven";
    private static final String COL_USER_CODE = "user_code";
    private static final String COL_USER_NAME = "user_name";
    private static final String COL_JOIN_FLAG = "join_flag";
    private static final String COL_JOIN_TIME = "join_time";
    private static final String COL_JOIN_LATE_FLAG = "join_late_flag";
    private static final String COL_WATCH_DURATION = "watch_duration";
    private static final String COL_ANSWER_FLAG = "answer_flag";
    private static final String COL_ANSWER_SCORE_TOTAL = "answer_score_total";
    private static final String COL_CREATE_TIME = "create_time";

    /** 必要列：缺任意一列都不导入（活动ID与战队ID是清空条件，工号与姓名是数据主体） */
    private static final String[] REQUIRED_COLUMNS = {COL_ACTIVITY_ID, COL_TEAM_ID, COL_USER_CODE, COL_USER_NAME};

    /** 支持的时间格式，按顺序尝试 */
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd"
    };

    private MeetingUserRecordExcelParser() {
    }

    /**
     * 解析导入模版Excel
     *
     * @param inputStream Excel输入流，由调用方负责关闭
     * @return 待导入的记录，保持Excel行顺序
     * @throws BusinessException 文件无法解析/缺少必要列/无数据行/存在不合法数据行
     */
    public static List<MeetingUserRealRecord> parse(InputStream inputStream) {
        Workbook workbook = null;
        try {
            workbook = WorkbookFactory.create(inputStream);
            if (workbook.getNumberOfSheets() <= 0) {
                throw new BusinessException(ResponseCode.MEETING_IMPORT_EXCEL_PARSE_ERROR);
            }
            Sheet sheet = workbook.getSheetAt(0);
            int headerRowNum = sheet.getFirstRowNum();
            Row headerRow = headerRowNum < 0 ? null : sheet.getRow(headerRowNum);
            if (headerRow == null) {
                throw new BusinessException(ResponseCode.MEETING_IMPORT_EXCEL_PARSE_ERROR);
            }

            Map<String, Integer> columns = resolveColumns(headerRow);
            for (String required : REQUIRED_COLUMNS) {
                if (!columns.containsKey(required)) {
                    LOGGER.warn("导入Excel缺少必要列 {}，解析到的表头={}", required, columns.keySet());
                    throw new BusinessException(ResponseCode.MEETING_IMPORT_EXCEL_MISSING_COLUMN);
                }
            }

            List<MeetingUserRealRecord> records = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            for (int rowNum = headerRowNum + 1; rowNum <= lastRowNum; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isBlankRow(row, columns)) {
                    continue;
                }
                // Excel 行号从 1 开始，报错信息里用真实行号，方便用户定位
                records.add(toEntity(row, columns, rowNum + 1));
            }
            if (records.isEmpty()) {
                throw new BusinessException(ResponseCode.MEETING_IMPORT_EXCEL_EMPTY);
            }
            LOGGER.info("共创会导入数据解析完成，行数={}", records.size());
            return records;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("解析共创会导入数据Excel失败", e);
            throw new BusinessException(ResponseCode.MEETING_IMPORT_EXCEL_PARSE_ERROR, e);
        } finally {
            closeQuietly(workbook);
        }
    }

    /**
     * 表头归一化：去掉空白/反引号/引号，并去掉导出SQL带出的 {@code +''} 尾巴，统一小写
     */
    private static String normalizeHeader(String header) {
        if (header == null) {
            return null;
        }
        String value = header.replaceAll("[\\s\\u3000`'\"]+", "").toLowerCase(Locale.ROOT);
        while (value.endsWith("+")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }

    /**
     * 建立「列名 -> 列下标」映射，同名取第一列
     */
    private static Map<String, Integer> resolveColumns(Row headerRow) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        short lastCellNum = headerRow.getLastCellNum();
        for (int i = 0; i < lastCellNum; i++) {
            String key = normalizeHeader(readText(headerRow.getCell(i)));
            if (key != null && !columns.containsKey(key)) {
                columns.put(key, i);
            }
        }
        return columns;
    }

    private static boolean isBlankRow(Row row, Map<String, Integer> columns) {
        for (Integer index : columns.values()) {
            if (!ExcelCellUtils.isBlank(readText(row.getCell(index)))) {
                return false;
            }
        }
        return true;
    }

    private static MeetingUserRealRecord toEntity(Row row, Map<String, Integer> columns, int excelRowNum) {
        Long activityId = readLong(row, columns, COL_ACTIVITY_ID, excelRowNum);
        Long teamId = readLong(row, columns, COL_TEAM_ID, excelRowNum);
        Long userCode = readLong(row, columns, COL_USER_CODE, excelRowNum);
        String userName = readText(row, columns, COL_USER_NAME);
        if (activityId == null || teamId == null || userCode == null || ExcelCellUtils.isBlank(userName)) {
            throw new BusinessException(ResponseCode.MEETING_IMPORT_EXCEL_ROW_ERROR.getCode(),
                    "第 " + excelRowNum + " 行数据不合法：activity_id / team_id / user_code / user_name 不能为空");
        }

        MeetingUserRealRecord entity = new MeetingUserRealRecord();
        entity.setActivityId(activityId);
        entity.setTeamId(teamId);
        entity.setTeamName(readText(row, columns, COL_TEAM_NAME));
        entity.setCityCode(readText(row, columns, COL_CITY_CODE));
        entity.setCityName(readText(row, columns, COL_CITY_NAME));
        entity.setDeptLevelOne(readText(row, columns, COL_DEPT_LEVEL_ONE));
        entity.setDeptLevelTwo(readText(row, columns, COL_DEPT_LEVEL_TWO));
        entity.setDeptLevelThree(readText(row, columns, COL_DEPT_LEVEL_THREE));
        entity.setDeptLevelFour(readText(row, columns, COL_DEPT_LEVEL_FOUR));
        entity.setDeptLevelFive(readText(row, columns, COL_DEPT_LEVEL_FIVE));
        entity.setDeptLevelSix(readText(row, columns, COL_DEPT_LEVEL_SIX));
        entity.setDeptLevelSeven(readText(row, columns, COL_DEPT_LEVEL_SEVEN));
        entity.setUserCode(userCode);
        entity.setUserName(userName.trim());
        entity.setJoinFlag(readInteger(row, columns, COL_JOIN_FLAG, excelRowNum, 0));
        entity.setJoinTime(readDate(row, columns, COL_JOIN_TIME, excelRowNum));
        entity.setJoinLateFlag(readInteger(row, columns, COL_JOIN_LATE_FLAG, excelRowNum, 0));
        entity.setWatchDuration(readLong(row, columns, COL_WATCH_DURATION, excelRowNum, 0L));
        entity.setAnswerFlag(readInteger(row, columns, COL_ANSWER_FLAG, excelRowNum, 0));
        entity.setAnswerScoreTotal(readDouble(row, columns, COL_ANSWER_SCORE_TOTAL, excelRowNum, 0D));
        entity.setCreateTime(readDate(row, columns, COL_CREATE_TIME, excelRowNum));
        return entity;
    }

    /* ---------- 单元格读取 ---------- */

    private static String readText(Cell cell) {
        return ExcelCellUtils.getRawText(cell);
    }

    private static String readText(Row row, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        return index == null ? null : readText(row.getCell(index));
    }

    private static Long readLong(Row row, Map<String, Integer> columns, String key, int excelRowNum) {
        String text = readText(row, columns, key);
        if (ExcelCellUtils.isBlank(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", "")).longValue();
        } catch (NumberFormatException e) {
            throw rowError(excelRowNum, key, text);
        }
    }

    private static Long readLong(Row row, Map<String, Integer> columns, String key, int excelRowNum, Long defaultValue) {
        Long value = readLong(row, columns, key, excelRowNum);
        return value == null ? defaultValue : value;
    }

    private static Integer readInteger(Row row, Map<String, Integer> columns, String key, int excelRowNum,
                                       Integer defaultValue) {
        Long value = readLong(row, columns, key, excelRowNum);
        return value == null ? defaultValue : value.intValue();
    }

    private static Double readDouble(Row row, Map<String, Integer> columns, String key, int excelRowNum,
                                     Double defaultValue) {
        String text = readText(row, columns, key);
        if (ExcelCellUtils.isBlank(text)) {
            return defaultValue;
        }
        try {
            return new BigDecimal(text.replace(",", "")).doubleValue();
        } catch (NumberFormatException e) {
            throw rowError(excelRowNum, key, text);
        }
    }

    /**
     * 读时间：日期格式的数值单元格直接取 Date，文本单元格按常见格式解析
     */
    private static Date readDate(Row row, Map<String, Integer> columns, String key, int excelRowNum) {
        Integer index = columns.get(key);
        Cell cell = index == null ? null : row.getCell(index);
        if (cell == null) {
            return null;
        }
        CellType type = ExcelCellUtils.resolveType(cell);
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue();
        }
        String text = readText(cell);
        if (ExcelCellUtils.isBlank(text)) {
            return null;
        }
        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
            format.setLenient(false);
            try {
                return format.parse(text);
            } catch (java.text.ParseException ignored) {
                // 继续尝试下一个格式
            }
        }
        throw new BusinessException(ResponseCode.MEETING_IMPORT_EXCEL_ROW_ERROR.getCode(),
                "第 " + excelRowNum + " 行的 " + key + " 不是合法时间：" + text);
    }

    private static BusinessException rowError(int excelRowNum, String key, String text) {
        return new BusinessException(ResponseCode.MEETING_IMPORT_EXCEL_ROW_ERROR.getCode(),
                "第 " + excelRowNum + " 行的 " + key + " 不是合法数字：" + text);
    }

    private static void closeQuietly(Workbook workbook) {
        if (workbook == null) {
            return;
        }
        try {
            workbook.close();
        } catch (IOException e) {
            LOGGER.warn("关闭导入Excel失败", e);
        }
    }
}
