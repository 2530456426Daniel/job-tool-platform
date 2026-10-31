package com.jobtool.platform.support.devtool;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Excel 单元格取值工具（Apache POI 5.x）。
 *
 * <p>提供两种口径，分别对应迁移前项目里的两个工具：</p>
 * <ul>
 *     <li>{@link #getIntegerText(Cell)}：数值按整数格式化，等价于原 {@code PoiUtils#getCellValue}
 *         的 {@code DecimalFormat("0")}，供 OKR 达成情况 Excel 使用；</li>
 *     <li>{@link #getRawText(Cell)}：数值按 {@link BigDecimal} 原样输出（100.5 不会被格式化成整数），
 *         供共创会导入模版使用。</li>
 * </ul>
 *
 * <p>公式单元格统一先取缓存结果类型，避免对字符串型公式调用数值方法而抛异常。</p>
 *
 * @author wangpengfei
 */
public final class ExcelCellUtils {

    /** OKR 导出文件里日期型单元格的展示格式 */
    public static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd";

    /** 共创会导入模版里时间型单元格的展示格式 */
    public static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private ExcelCellUtils() {
    }

    /**
     * 按整数口径读文本（原 PoiUtils 行为）：日期 → yyyy-MM-dd，数值 → 整数。
     *
     * @param cell 单元格，可为 null
     * @return 文本；空白单元格返回 null
     */
    public static String getIntegerText(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = resolveType(cell);
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return new SimpleDateFormat(DEFAULT_DATE_PATTERN, Locale.ROOT).format(cell.getDateCellValue());
            }
            return new DecimalFormat("0").format(cell.getNumericCellValue());
        }
        return readNonNumeric(cell, type);
    }

    /**
     * 按原值口径读文本：日期 → 指定格式，数值 → BigDecimal 去除多余 0 后的十进制字符串。
     *
     * @param cell        单元格，可为 null
     * @param datePattern 日期型单元格的格式
     * @return 文本；空白单元格返回 null
     */
    public static String getRawText(Cell cell, String datePattern) {
        if (cell == null) {
            return null;
        }
        CellType type = resolveType(cell);
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return new SimpleDateFormat(datePattern, Locale.ROOT).format(cell.getDateCellValue());
            }
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return readNonNumeric(cell, type);
    }

    /**
     * 按原值口径读文本，日期使用 {@link #DEFAULT_DATE_TIME_PATTERN}
     */
    public static String getRawText(Cell cell) {
        return getRawText(cell, DEFAULT_DATE_TIME_PATTERN);
    }

    /**
     * 读行内指定列的整数口径文本
     */
    public static String integerText(Row row, Integer columnIndex) {
        return row == null || columnIndex == null ? null : trimToNull(getIntegerText(row.getCell(columnIndex)));
    }

    /**
     * 读行内指定列的原值口径文本
     */
    public static String rawText(Row row, Integer columnIndex) {
        return row == null || columnIndex == null ? null : trimToNull(getRawText(row.getCell(columnIndex)));
    }

    /**
     * 解析单元格实际类型：公式单元格取缓存结果类型
     */
    public static CellType resolveType(Cell cell) {
        CellType type = cell.getCellType();
        return type == CellType.FORMULA ? cell.getCachedFormulaResultType() : type;
    }

    /**
     * 非数值型单元格取值
     */
    private static String readNonNumeric(Cell cell, CellType type) {
        if (type == CellType.STRING) {
            return trimToNull(cell.getStringCellValue());
        }
        if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        return null;
    }

    /**
     * 去首尾空白，空串归一为 null
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 判断是否空白（null 或全空白）
     */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
