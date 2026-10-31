package com.jobtool.platform.service.devtool;

import com.jobtool.platform.common.BusinessException;
import com.jobtool.platform.common.ResponseCode;
import com.jobtool.platform.dto.devtool.MeetingCollectionRecord;
import com.jobtool.platform.dto.devtool.MeetingCollectionSummary;
import com.jobtool.platform.dto.devtool.MeetingUserCollectionListResponse;
import com.jobtool.platform.dto.devtool.MeetingUserCollectionRequest;
import com.jobtool.platform.dto.devtool.MeetingUserRecordImportResponse;
import com.jobtool.platform.support.devtool.MeetingCollectionExcelWriter;
import com.jobtool.platform.support.devtool.MeetingNameListParser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 共创会人员参会情况统计 Service 集成测试。
 *
 * <p>使用应用真实的 H2 内存库与 MyBatis（schema.sql 自动建表），覆盖
 * 导入（先清空再插入）→ 查询（名单交集 + 指标计算 + 战队汇总）→ 导出 Excel 全链路。</p>
 *
 * @author wangpengfei
 */
@SpringBootTest
class MeetingUserCollectionServiceImplTest {

    /** 导入模版 21 列表头，与 meeting_user_real_record 字段一一对应 */
    private static final String[] IMPORT_HEADERS = {
            "activity_id", "team_id", "team_name", "city_code", "city_name",
            "dept_level_one", "dept_level_two", "dept_level_three", "dept_level_four",
            "dept_level_five", "dept_level_six", "dept_level_seven",
            "user_code", "user_name", "join_flag", "join_time", "join_late_flag",
            "watch_duration", "answer_flag", "answer_score_total", "create_time"
    };

    /** 名单条数从 classpath 实际解析，名单更新后测试无需跟着改 */
    private static final int ROSTER_SIZE = MeetingNameListParser.parse(
            MeetingUserCollectionServiceImplTest.class.getResourceAsStream("/docs/meeting/meetingNameList.md")).size();

    private static final long ACTIVITY_ID = 645L;

    @Autowired
    private MeetingUserCollectionService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from meeting_user_real_record");
        jdbcTemplate.update("delete from meeting_activity");
        jdbcTemplate.update("""
                        insert into meeting_activity
                            (id, meeting_name, meeting_city_code, meeting_city_name, meeting_begin_time)
                        values (?, ?, ?, ?, ?)
                        """,
                ACTIVITY_ID, "集团7月共创会", "100100", "北京",
                Timestamp.valueOf("2026-07-02 14:00:00"));
    }

    @Test
    void importThenQueryShouldMatchRosterAndCalculateMetrics() {
        MeetingUserRecordImportResponse imported = service.importExcel(file("meeting-import.xlsx", importWorkbook(
                // 名单内：罗乾勇 20120148
                row(20120148L, "罗乾勇", 1, 4140L, 1, 100D),
                // 名单外：应被过滤
                row(99999999L, "名单外", 1, 100L, 0, 50D)
        )));

        assertEquals(2, imported.getTotal().intValue());
        assertEquals(0, imported.getDeletedTotal().intValue());
        assertEquals(2, imported.getInsertedTotal().intValue());
        assertEquals(1, imported.getGroups().size());
        assertEquals(Long.valueOf(ACTIVITY_ID), imported.getGroups().get(0).getActivityId());
        assertEquals("K2", imported.getGroups().get(0).getTeamName());
        assertEquals(2, imported.getGroups().get(0).getRowCount().intValue());

        MeetingUserCollectionRequest request = request(ACTIVITY_ID, 100D, 4140L);
        MeetingUserCollectionListResponse data = service.list(request);

        assertEquals(Long.valueOf(ACTIVITY_ID), data.getActivityId());
        assertEquals("集团7月共创会", data.getMeetingName());
        assertEquals("2026-07-02", data.getMeetingDate());
        assertEquals("classpath:docs/meeting/meetingNameList.md", data.getNameListPath());
        assertEquals(ROSTER_SIZE, data.getNameListTotal().intValue());
        assertEquals(2, data.getRecordTotal().intValue());
        assertEquals(1, data.getMatchedTotal().intValue());
        assertEquals(1, data.getExcludedNotInNameList().intValue());
        assertEquals(ROSTER_SIZE - 1, data.getExcludedNoRecord().intValue());
        assertEquals(1, data.getTotal().intValue());

        MeetingCollectionRecord record = data.getList().get(0);
        assertEquals("20120148", record.getUserCode());
        assertEquals("罗乾勇", record.getUserName());
        assertEquals("K2", record.getTeamName());
        assertEquals(100D, record.getAnswerScoreTotal().doubleValue(), 0.001D);
        assertEquals(4140L, record.getWatchDuration().longValue());
        assertEquals(100D, record.getJoinRate().doubleValue(), 0.001D);
        assertEquals(100D, record.getAnswerRate().doubleValue(), 0.001D);
        assertEquals(100D, record.getAnswerAccuracy().doubleValue(), 0.001D);
        assertEquals(100D, record.getWatchRate().doubleValue(), 0.001D);
        assertEquals(100D, record.getScore().doubleValue(), 0.001D);

        // 汇总按整个战队口径（含名单外人员），总人数为 2
        MeetingCollectionSummary summary = data.getSummary();
        assertNotNull(summary);
        assertEquals("K2", summary.getTeamName());
        assertEquals(2, summary.getTotalCount().intValue());
        assertEquals(75D, summary.getAvgAnswerScore().doubleValue(), 0.001D);
        assertEquals(2120D, summary.getAvgWatchDuration().doubleValue(), 0.001D);
        assertEquals(100D, summary.getJoinRate().doubleValue(), 0.001D);
        assertEquals(50D, summary.getAnswerRate().doubleValue(), 0.001D);
        assertEquals(75D, summary.getAnswerAccuracy().doubleValue(), 0.001D);
        assertEquals(51.2D, summary.getWatchRate().doubleValue(), 0.001D);
        assertEquals(69.1D, summary.getScore().doubleValue(), 0.001D);
    }

    @Test
    void importAgainShouldReplaceSameActivityAndTeam() {
        byte[] excel = importWorkbook(row(20120148L, "罗乾勇", 1, 4140L, 1, 100D));

        service.importExcel(file("meeting-import.xlsx", excel));
        MeetingUserRecordImportResponse second = service.importExcel(file("meeting-import.xlsx", excel));

        assertEquals(1, second.getInsertedTotal().intValue());
        assertEquals(1, second.getDeletedTotal().intValue());
        assertEquals(1, service.list(request(ACTIVITY_ID, 100D, 4140L)).getRecordTotal().intValue());
    }

    @Test
    void exportShouldWriteTemplateLayout() throws Exception {
        service.importExcel(file("meeting-import.xlsx", importWorkbook(row(20120148L, "罗乾勇", 1, 4140L, 1, 100D))));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.export(request(ACTIVITY_ID, 100D, 4140L), out);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(MeetingCollectionExcelWriter.SHEET_NAME, sheet.getSheetName());

            Row header = sheet.getRow(0);
            assertEquals("用户编号", header.getCell(0).getStringCellValue());
            assertEquals("观看时长（单位秒）", header.getCell(3).getStringCellValue());
            assertEquals("分数", header.getCell(8).getStringCellValue());
            assertEquals("共创会名称", header.getCell(10).getStringCellValue());
            assertEquals("活动日期", header.getCell(12).getStringCellValue());

            Row detail = sheet.getRow(1);
            assertEquals("20120148", detail.getCell(0).getStringCellValue());
            assertEquals("罗乾勇", detail.getCell(1).getStringCellValue());
            assertEquals("100.00", detail.getCell(2).getStringCellValue());
            assertEquals("4140", detail.getCell(3).getStringCellValue());
            assertEquals("100.0", detail.getCell(4).getStringCellValue());
            assertEquals(100D, detail.getCell(8).getNumericCellValue(), 0.001D);
            assertEquals("集团7月共创会", detail.getCell(10).getStringCellValue());
            assertEquals(ACTIVITY_ID, (long) detail.getCell(11).getNumericCellValue());

            // 明细 1 行 + 3 行空行后是汇总表头，再下一行是汇总数据
            Row summaryHeader = sheet.getRow(5);
            assertEquals("用户编号", summaryHeader.getCell(0).getStringCellValue());
            assertEquals("总人数", summaryHeader.getCell(1).getStringCellValue());
            Row summary = sheet.getRow(6);
            assertEquals("K2", summary.getCell(0).getStringCellValue());
            assertEquals("1", summary.getCell(1).getStringCellValue());
        }
    }

    @Test
    void exportShouldRejectWhenNoData() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.export(request(ACTIVITY_ID, 100D, 4140L), new ByteArrayOutputStream()));
        assertEquals(ResponseCode.MEETING_NO_EXPORT_DATA.getCode(), ex.getCode());
    }

    @Test
    void queryShouldValidateParameters() {
        assertEquals(ResponseCode.APPLICATION_PARAMETER_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.list(null)).getCode());
        assertEquals(ResponseCode.MEETING_ACTIVITY_ID_INVALID.getCode(),
                assertThrows(BusinessException.class, () -> service.list(request(0L, 100D, 4140L))).getCode());
        assertEquals(ResponseCode.MEETING_ANSWER_SCORE_INVALID.getCode(),
                assertThrows(BusinessException.class,
                        () -> service.list(request(ACTIVITY_ID, 0D, 4140L))).getCode());
        assertEquals(ResponseCode.MEETING_ATTENDANCE_DURATION_INVALID.getCode(),
                assertThrows(BusinessException.class,
                        () -> service.list(request(ACTIVITY_ID, 100D, 0L))).getCode());
    }

    @Test
    void importShouldRejectInvalidFile() {
        assertEquals(ResponseCode.MEETING_IMPORT_FILE_TYPE_ERROR.getCode(),
                assertThrows(BusinessException.class,
                        () -> service.importExcel(file("data.csv", "x".getBytes()))).getCode());

        MockMultipartFile empty = new MockMultipartFile("file", "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
        assertEquals(ResponseCode.APPLICATION_PARAMETER_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.importExcel(empty)).getCode());

        assertNull(service.list(request(ACTIVITY_ID, 100D, 4140L)).getSummary());
    }

    /* ---------- 辅助 ---------- */

    private static MeetingUserCollectionRequest request(Long activityId, Double answerScore, Long attendanceDuration) {
        MeetingUserCollectionRequest request = new MeetingUserCollectionRequest();
        request.setActivityId(activityId);
        request.setAnswerScore(answerScore);
        request.setAttendanceDuration(attendanceDuration);
        return request;
    }

    private static MockMultipartFile file(String fileName, byte[] content) {
        return new MockMultipartFile("file", fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    /**
     * 构造一行导入数据（按 {@link #IMPORT_HEADERS} 的列顺序）
     */
    private static Object[] row(Long userCode, String userName, int joinFlag, Long watchDuration,
                                int answerFlag, Double answerScoreTotal) {
        return new Object[]{ACTIVITY_ID, 1L, "K2", "100100", "北京",
                "D1", "D2", "D3", "D4", "D5", "D6", "D7",
                userCode, userName, joinFlag, null, 0, watchDuration, answerFlag, answerScoreTotal, null};
    }

    private static byte[] importWorkbook(Object[]... rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                header.createCell(i).setCellValue(IMPORT_HEADERS[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                Object[] values = rows[r];
                for (int c = 0; c < values.length; c++) {
                    Object value = values[c];
                    if (value == null) {
                        continue;
                    }
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else {
                        row.createCell(c).setCellValue(String.valueOf(value));
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("构造测试 Excel 失败", e);
        }
    }
}
