package com.jobtool.platform.support.devtool;

import com.jobtool.platform.dto.devtool.MeetingCollectionRecord;
import com.jobtool.platform.dto.devtool.MeetingCollectionSummary;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 共创会人员参会情况统计 - 导出模板生成。
 *
 * <p>逐格对齐样例文件（实测）：</p>
 * <pre>
 * 第 1 行            A1:I1 九列表头（文本格式）、K1:M1 共创会名称/共创会ID/活动日期
 * 第 2 ~ R 行        明细，A–H 写文本（100.00 / 4054 / 100.0 / 97.9），I 写数值
 * 第 2 行            K2/L2/M2 参数块（只写第一条明细行）
 * 第 R+1 ~ R+3 行    空行（样例为 3 行）
 * 第 R+4 行          汇总表头
 * 第 R+5 行          汇总数据：A 战队名(文本)、B 总人数(文本)、C–H 数值、I 数值
 * </pre>
 *
 * <p>样例文件的「分数」列是公式，本实现直接写计算结果数值，数值与公式重算结果一致，
 * 且不依赖 Excel 打开时重算。</p>
 *
 * @author wangpengfei
 */
public final class MeetingCollectionExcelWriter {

    /** 工作表名，与样例一致 */
    public static final String SHEET_NAME = "Sheet1";

    /** 明细表头，与样例第 1 行 A1:I1 逐字一致 */
    private static final String[] HEADERS = {
            "用户编号", "姓名", "答题分数", "观看时长（单位秒）", "参会率", "答题率", "答题准确率", "观看率", "分数"
    };

    /** 汇总表头：A 列在模板中写作「用户编号」但值为战队名，照抄模板不改 */
    private static final String[] SUMMARY_HEADERS = {
            "用户编号", "总人数", "答题平均分数", "观看平均时长（单位秒）", "参会率", "答题率", "答题准确率", "观看率", "分数"
    };

    /** 参数块表头 */
    private static final String[] MEETING_HEADERS = {"共创会名称", "共创会ID", "活动日期"};

    /** 明细区与汇总区之间的空行数（样例为 3 行） */
    private static final int BLANK_ROWS = 3;

    /** 表头行高（样例 17） */
    private static final float HEADER_ROW_HEIGHT = 17f;

    /** 列宽（样例实测值，单位为字符），其余列用默认宽度 */
    private static final double WIDTH_COLUMN_A = 13.015625;
    private static final double WIDTH_COLUMN_D = 18;
    private static final double WIDTH_COLUMN_K = 13.671875;
    private static final double WIDTH_COLUMN_M = 9.625;

    private MeetingCollectionExcelWriter() {
    }

    /**
     * 生成导出文件
     *
     * @param outputStream       输出流，由调用方负责关闭
     * @param activityId         活动id（写入 L2）
     * @param meetingName        共创会名称（写入 K2，可为 null）
     * @param meetingDate        活动日期（写入 M2，可为 null）
     * @param records            明细，不能为空（导出前已校验）
     * @param summary            战队汇总，可为 null
     * @throws IOException 写出失败
     */
    public static void write(OutputStream outputStream,
                             Long activityId,
                             String meetingName,
                             Date meetingDate,
                             List<MeetingCollectionRecord> records,
                             MeetingCollectionSummary summary) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        try {
            Sheet sheet = workbook.createSheet(SHEET_NAME);

            CellStyle textStyle = workbook.createCellStyle();
            DataFormat dataFormat = workbook.createDataFormat();
            textStyle.setDataFormat(dataFormat.getFormat("@"));

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(dataFormat.getFormat("mm-dd-yy"));

            // 表头行（第 1 行）
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(HEADER_ROW_HEIGHT);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(textStyle);
            }
            for (int i = 0; i < MEETING_HEADERS.length; i++) {
                headerRow.createCell(10 + i).setCellValue(MEETING_HEADERS[i]);
            }

            // 明细区
            int lastDataRowIndex = 0;
            for (int i = 0; i < records.size(); i++) {
                MeetingCollectionRecord record = records.get(i);
                Row row = sheet.createRow(i + 1);
                lastDataRowIndex = i + 1;
                row.createCell(0).setCellValue(nullToEmpty(record.getUserCode()));
                row.createCell(1).setCellValue(nullToEmpty(record.getUserName()));
                row.createCell(2).setCellValue(format2(record.getAnswerScoreTotal()));
                row.createCell(3).setCellValue(record.getWatchDuration() == null
                        ? "0" : String.valueOf(record.getWatchDuration()));
                row.createCell(4).setCellValue(format1(record.getJoinRate()));
                row.createCell(5).setCellValue(format1(record.getAnswerRate()));
                row.createCell(6).setCellValue(format1(record.getAnswerAccuracy()));
                row.createCell(7).setCellValue(format1(record.getWatchRate()));
                row.createCell(8).setCellValue(record.getScore() == null ? 0D : record.getScore());
            }

            // 参数块：只写在第一条明细行（样例如此）；取不到共创会名称时该单元格留空
            if (!records.isEmpty()) {
                Row firstRow = sheet.getRow(1);
                if (meetingName != null && !meetingName.isEmpty()) {
                    firstRow.createCell(10).setCellValue(meetingName);
                }
                firstRow.createCell(11).setCellValue(activityId == null ? 0D : activityId.doubleValue());
                Cell dateCell = firstRow.createCell(12);
                if (meetingDate != null) {
                    dateCell.setCellValue(meetingDate);
                    dateCell.setCellStyle(dateStyle);
                }
            }

            // 空行
            for (int i = 1; i <= BLANK_ROWS; i++) {
                sheet.createRow(lastDataRowIndex + i);
            }

            // 汇总区
            int summaryHeaderIndex = lastDataRowIndex + BLANK_ROWS + 1;
            Row summaryHeaderRow = sheet.createRow(summaryHeaderIndex);
            for (int i = 0; i < SUMMARY_HEADERS.length; i++) {
                Cell cell = summaryHeaderRow.createCell(i);
                cell.setCellValue(SUMMARY_HEADERS[i]);
                cell.setCellStyle(textStyle);
            }
            if (summary != null) {
                Row summaryRow = sheet.createRow(summaryHeaderIndex + 1);
                summaryRow.setHeightInPoints(HEADER_ROW_HEIGHT);
                // 样例汇总行整行都是「文本」格式：A 战队名与 B 总人数为文本，C–I 为数值但套用文本格式
                Cell[] cells = new Cell[SUMMARY_HEADERS.length];
                cells[0] = summaryRow.createCell(0);
                cells[0].setCellValue(nullToEmpty(summary.getTeamName()));
                cells[1] = summaryRow.createCell(1);
                cells[1].setCellValue(summary.getTotalCount() == null
                        ? "0" : String.valueOf(summary.getTotalCount()));
                cells[2] = summaryRow.createCell(2);
                cells[2].setCellValue(nullToZero(summary.getAvgAnswerScore()));
                cells[3] = summaryRow.createCell(3);
                cells[3].setCellValue(nullToZero(summary.getAvgWatchDuration()));
                cells[4] = summaryRow.createCell(4);
                cells[4].setCellValue(nullToZero(summary.getJoinRate()));
                cells[5] = summaryRow.createCell(5);
                cells[5].setCellValue(nullToZero(summary.getAnswerRate()));
                cells[6] = summaryRow.createCell(6);
                cells[6].setCellValue(nullToZero(summary.getAnswerAccuracy()));
                cells[7] = summaryRow.createCell(7);
                cells[7].setCellValue(nullToZero(summary.getWatchRate()));
                cells[8] = summaryRow.createCell(8);
                cells[8].setCellValue(nullToZero(summary.getScore()));
                for (Cell cell : cells) {
                    cell.setCellStyle(textStyle);
                }
            }

            // 列宽（样例实测）
            setColumnWidth(sheet, 0, WIDTH_COLUMN_A);
            setColumnWidth(sheet, 3, WIDTH_COLUMN_D);
            setColumnWidth(sheet, 10, WIDTH_COLUMN_K);
            setColumnWidth(sheet, 12, WIDTH_COLUMN_M);

            workbook.write(outputStream);
            outputStream.flush();
        } finally {
            workbook.close();
        }
    }

    private static void setColumnWidth(Sheet sheet, int columnIndex, double widthInChars) {
        sheet.setColumnWidth(columnIndex, (int) Math.round(widthInChars * 256));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static double nullToZero(Double value) {
        return value == null ? 0D : value;
    }

    /** 答题分数：两位小数文本（样例为 100.00） */
    private static String format2(Double value) {
        return String.format(Locale.ROOT, "%.2f", value == null ? 0D : value);
    }

    /** 各率：一位小数文本（样例为 100.0、97.9） */
    private static String format1(Double value) {
        return String.format(Locale.ROOT, "%.1f", value == null ? 0D : value);
    }
}
