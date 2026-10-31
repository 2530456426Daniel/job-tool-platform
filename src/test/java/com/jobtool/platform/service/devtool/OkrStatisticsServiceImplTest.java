package com.jobtool.platform.service.devtool;

import com.jobtool.platform.common.BusinessException;
import com.jobtool.platform.common.ResponseCode;
import com.jobtool.platform.dto.devtool.OkrFilterOption;
import com.jobtool.platform.dto.devtool.OkrStatisticsListResponse;
import com.jobtool.platform.dto.devtool.OkrStatisticsRecord;
import com.jobtool.platform.dto.devtool.OkrStatisticsUploadResponse;
import com.jobtool.platform.service.devtool.impl.OkrStatisticsServiceImpl;
import com.jobtool.platform.support.devtool.OkrNameListParser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OKR达成情况统计 Service 测试。
 *
 * <p>覆盖真实链路：POI 解析 Excel → 读取 classpath 人员名单 → 取交集 → 筛选项聚合 → 清空。
 * 不加载 Spring 容器、不依赖数据库。</p>
 *
 * @author wangpengfei
 */
class OkrStatisticsServiceImplTest {

    /** 表头与真实导出文件一致（列顺序刻意打乱，验证按表头名匹配而不是按列下标） */
    private static final String[] HEADERS = {
            "姓名", "工号", "上月OKR达成复盘率", "本月OKRI是否及时发布",
            "增长曲线是否更新完成", "是否是经理及以上", "O对齐率"
    };

    /** 名单条数从 classpath 实际解析，名单更新后测试无需跟着改 */
    private static final int ROSTER_SIZE = OkrNameListParser.parse(
            OkrStatisticsServiceImplTest.class.getResourceAsStream("/docs/okr/okrNameList.md")).size();

    private final OkrStatisticsService service = new OkrStatisticsServiceImpl();

    @Test
    void uploadShouldKeepRosterMembersAndBuildFilters() {
        byte[] excel = workbook(
                new String[]{"张琴", "20212415", "100%", "是", "完成", "是", ""},
                new String[]{"王珏", "60024713", "80%", "否", "未完成", "否", "50%"},
                // 不在名单里，上传后应被过滤
                new String[]{"名单外", "99999999", "100%", "是", "完成", "是", "100%"}
        );

        OkrStatisticsUploadResponse meta = service.upload(file("OKR_2026年10月_01.xlsx", excel));

        assertEquals(3, meta.getOkrTotal().intValue());
        assertEquals(ROSTER_SIZE, meta.getNameListTotal().intValue());
        assertEquals(2, meta.getMatchedTotal().intValue());
        assertEquals(1, meta.getExcludedNotInNameList().intValue());
        assertEquals(ROSTER_SIZE - 2, meta.getExcludedNotInOkr().intValue());
        assertNotNull(meta.getUploadedAt());
        assertEquals("classpath:docs/okr/okrNameList.md", meta.getNameListPath());

        OkrStatisticsListResponse list = service.list();
        assertTrue(list.isUploaded());
        assertEquals(2, list.getTotal().intValue());
        assertEquals(2, list.getList().size());

        OkrStatisticsRecord zhang = find(list, "20212415");
        assertEquals("张琴", zhang.getName());
        // O对齐率源值为空 -> 归一化为“不适用”，与“未对齐0%”区分
        assertEquals("不适用", zhang.getAlignRate());

        OkrStatisticsRecord wang = find(list, "60024713");
        assertEquals("50%", wang.getAlignRate());

        // 筛选项：复盘率按数值降序，O对齐率中能解析数值的排在“不适用”前面
        assertOptionValues(list, "reviewRate", "100%", "80%");
        assertOptionValues(list, "oAlignRate", "50%", "不适用");
        // 枚举类按固定业务顺序：是 -> 否
        assertOptionValues(list, "okrPublished", "是", "否");
        assertOptionValues(list, "growthCurveUpdated", "完成", "未完成");
        // 姓名与工号按工号升序
        assertOptionValues(list, "employeeNo", "20212415", "60024713");
        assertOptionValues(list, "name", "张琴", "王珏");

        OkrFilterOption publishedYes = list.getFilters().get("okrPublished").get(0);
        assertEquals("是（1）", publishedYes.getLabel());
        assertEquals(1, publishedYes.getCount().intValue());
    }

    @Test
    void duplicateRowsShouldKeepFirstOnly() {
        byte[] excel = workbook(
                new String[]{"张琴", "20212415", "100%", "是", "完成", "是", "100%"},
                new String[]{"张琴（代）", "20212415", "50%", "否", "未完成", "否", "0%"}
        );

        OkrStatisticsUploadResponse meta = service.upload(file("dup.xlsx", excel));

        assertEquals(2, meta.getOkrTotal().intValue());
        assertEquals(1, meta.getMatchedTotal().intValue());
        // 重复行被忽略，且不计入“名单外过滤”
        assertEquals(0, meta.getExcludedNotInNameList().intValue());
        assertEquals("100%", service.list().getList().get(0).getReviewRate());
    }

    @Test
    void clearShouldResetSnapshot() {
        service.upload(file("okr.xlsx", workbook(
                new String[]{"张琴", "20212415", "100%", "是", "完成", "是", "100%"})));

        service.clear();

        OkrStatisticsListResponse list = service.list();
        assertFalse(list.isUploaded());
        assertEquals(0, list.getTotal().intValue());
        assertTrue(list.getList().isEmpty());
        assertTrue(list.getFilters().isEmpty());
    }

    @Test
    void uploadShouldRejectNonExcelFile() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload(file("okr.txt", "not excel".getBytes())));
        assertEquals(ResponseCode.OKR_FILE_TYPE_ERROR.getCode(), ex.getCode());
    }

    @Test
    void uploadShouldRejectEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "okr.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload(empty));
        assertEquals(ResponseCode.APPLICATION_PARAMETER_ERROR.getCode(), ex.getCode());
    }

    /* ---------- 辅助 ---------- */

    private static MockMultipartFile file(String fileName, byte[] content) {
        return new MockMultipartFile("file", fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    /**
     * 生成与真实导出文件同表头的 xlsx
     */
    private static byte[] workbook(String[]... rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("构造测试 Excel 失败", e);
        }
    }

    private static OkrStatisticsRecord find(OkrStatisticsListResponse list, String employeeNo) {
        return list.getList().stream()
                .filter(record -> employeeNo.equals(record.getEmployeeNo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到工号 " + employeeNo));
    }

    private static void assertOptionValues(OkrStatisticsListResponse list, String field, String... expected) {
        List<OkrFilterOption> options = list.getFilters().get(field);
        assertNotNull(options, "缺少筛选项：" + field);
        assertEquals(expected.length, options.size(), "筛选项数量不符：" + field);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], options.get(i).getValue(), "第 " + (i + 1) + " 项不符：" + field);
        }
    }
}
