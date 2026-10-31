package com.jobtool.platform.support.devtool;

import com.jobtool.platform.common.BusinessException;
import com.jobtool.platform.common.ResponseCode;
import com.jobtool.platform.dto.devtool.OkrStatisticsRecord;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OKR达成情况统计 - 达成情况Excel解析工具。
 *
 * <p>解析约定（依据真实导出文件实测）：</p>
 * <ul>
 *     <li>取第一个 sheet，不硬编码 sheet 名</li>
 *     <li>表头在第 1 行，数据从第 2 行开始</li>
 *     <li>表头匹配前先归一化：去掉所有空白字符（含全角空格），
 *         因为真实表头存在 {@code 主职 部门名称} 这种内部带空格的列名</li>
 * </ul>
 *
 * @author wangpengfei
 */
public final class OkrExcelParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(OkrExcelParser.class);

    /** 表头：姓名 */
    private static final String HEADER_NAME = "姓名";

    /** 表头：工号（匹配主键） */
    private static final String HEADER_EMPLOYEE_NO = "工号";

    /** 表头：上月OKR达成复盘率 */
    private static final String HEADER_REVIEW_RATE = "上月OKR达成复盘率";

    /** 表头：本月OKRI是否及时发布 */
    private static final String HEADER_OKR_PUBLISHED = "本月OKRI是否及时发布";

    /** 表头：增长曲线是否更新完成 */
    private static final String HEADER_GROWTH_CURVE = "增长曲线是否更新完成";

    /** 表头：是否是经理及以上 */
    private static final String HEADER_MANAGER = "是否是经理及以上";

    /** 表头：O对齐率 */
    private static final String HEADER_O_ALIGN_RATE = "O对齐率";

    private OkrExcelParser() {
    }

    /**
     * 解析达成情况Excel
     *
     * @param inputStream Excel输入流，由调用方负责关闭
     * @return 记录列表，保持Excel原始行顺序；O对齐率保留原始值（可能为null）
     * @throws BusinessException 解析失败或缺少必要列时抛出
     */
    public static List<OkrStatisticsRecord> parse(InputStream inputStream) {
        Workbook workbook = null;
        try {
            workbook = WorkbookFactory.create(inputStream);
            if (workbook.getNumberOfSheets() <= 0) {
                throw new BusinessException(ResponseCode.OKR_EXCEL_PARSE_ERROR);
            }
            Sheet sheet = workbook.getSheetAt(0);
            int headerRowNum = sheet.getFirstRowNum();
            Row headerRow = headerRowNum < 0 ? null : sheet.getRow(headerRowNum);
            if (headerRow == null) {
                throw new BusinessException(ResponseCode.OKR_EXCEL_PARSE_ERROR);
            }

            Map<String, Integer> columnIndex = resolveColumns(headerRow);
            // 工号、姓名为必需列
            if (!columnIndex.containsKey(HEADER_EMPLOYEE_NO) || !columnIndex.containsKey(HEADER_NAME)) {
                LOGGER.warn("达成Excel缺少必要列，解析到的表头={}", columnIndex.keySet());
                throw new BusinessException(ResponseCode.OKR_EXCEL_MISSING_COLUMN);
            }

            List<OkrStatisticsRecord> records = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            for (int rowNum = headerRowNum + 1; rowNum <= lastRowNum; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) {
                    continue;
                }
                String employeeNo = ExcelCellUtils.integerText(row, columnIndex.get(HEADER_EMPLOYEE_NO));
                String name = ExcelCellUtils.integerText(row, columnIndex.get(HEADER_NAME));
                // 工号与姓名都为空视为空行，跳过
                if (ExcelCellUtils.isBlank(employeeNo) && ExcelCellUtils.isBlank(name)) {
                    continue;
                }
                OkrStatisticsRecord record = new OkrStatisticsRecord();
                record.setName(name);
                record.setEmployeeNo(employeeNo);
                record.setReviewRate(ExcelCellUtils.integerText(row, columnIndex.get(HEADER_REVIEW_RATE)));
                record.setOkrPublished(ExcelCellUtils.integerText(row, columnIndex.get(HEADER_OKR_PUBLISHED)));
                record.setGrowthCurveUpdated(ExcelCellUtils.integerText(row, columnIndex.get(HEADER_GROWTH_CURVE)));
                record.setManagerOrAbove(ExcelCellUtils.integerText(row, columnIndex.get(HEADER_MANAGER)));
                // O对齐率保留原始值，为空时由服务层统一归一化为“不适用”
                record.setAlignRate(ExcelCellUtils.integerText(row, columnIndex.get(HEADER_O_ALIGN_RATE)));
                records.add(record);
            }
            LOGGER.info("达成Excel解析完成，sheet={}，记录数={}", sheet.getSheetName(), records.size());
            return records;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("解析达成Excel失败", e);
            throw new BusinessException(ResponseCode.OKR_EXCEL_PARSE_ERROR, e);
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (Exception e) {
                    LOGGER.warn("关闭达成Excel工作簿失败", e);
                }
            }
        }
    }

    /**
     * 建立 归一化表头 -> 列下标 的映射
     */
    private static Map<String, Integer> resolveColumns(Row headerRow) {
        Map<String, Integer> columnIndex = new HashMap<>();
        short lastCellNum = headerRow.getLastCellNum();
        for (int col = 0; col < lastCellNum; col++) {
            String raw = ExcelCellUtils.integerText(headerRow, col);
            if (ExcelCellUtils.isBlank(raw)) {
                continue;
            }
            String normalized = raw.replaceAll("[\\s\\u3000]+", "");
            columnIndex.putIfAbsent(normalized, col);
        }
        return columnIndex;
    }
}
