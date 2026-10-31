package com.jobtool.platform.support.devtool;

import com.jobtool.platform.common.BusinessException;
import com.jobtool.platform.common.ResponseCode;
import com.jobtool.platform.dto.devtool.MeetingRosterItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 共创会人员参会情况统计 - 人员名单（meetingNameList.md）解析工具。
 *
 * <p>名单的实际载体是 Markdown 文档，正文由 {@code ```json} 代码块包裹，
 * 内容为 {@code [{"name":"罗乾勇","code":"20120148"}]} 形式的 JSON 数组。
 * 因此解析顺序为：</p>
 * <ol>
 *     <li>先整体按 JSON 数组解析（兼容纯 .json / .txt 内容）</li>
 *     <li>失败则剥离 Markdown 代码围栏后再解析（兼容 .md 文档）</li>
 * </ol>
 *
 * <p>与 OKR 名单不同：共创会名单里单条脏数据不阻断整体统计，跳过并记日志。</p>
 *
 * @author wangpengfei
 */
public final class MeetingNameListParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeetingNameListParser.class);

    /** Markdown 代码围栏，兼容 ```json 与 ``` 两种写法 */
    private static final Pattern CODE_FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /** UTF-8 BOM */
    private static final String UTF8_BOM = "\uFEFF";

    private MeetingNameListParser() {
    }

    /**
     * 读取并解析名单文件
     *
     * @param file 名单文件
     * @return 名单条目列表，保持文件中的原始顺序
     * @throws BusinessException 文件不存在/不可读/格式非法/为空/无有效条目
     */
    public static List<MeetingRosterItem> parse(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            LOGGER.warn("共创会人员名单文件不存在或不是文件：{}", file == null ? null : file.getAbsolutePath());
            throw new BusinessException(ResponseCode.MEETING_NAME_LIST_READ_ERROR);
        }
        String content;
        try {
            content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("读取共创会人员名单文件失败：{}", file.getAbsolutePath(), e);
            throw new BusinessException(ResponseCode.MEETING_NAME_LIST_READ_ERROR, e);
        }
        return parse(content);
    }

    /**
     * 读取并解析名单输入流（用于 classpath 资源）
     *
     * @param inputStream 名单内容输入流，由调用方负责关闭
     * @return 名单条目列表
     */
    public static List<MeetingRosterItem> parse(InputStream inputStream) {
        if (inputStream == null) {
            LOGGER.warn("共创会人员名单输入流为空");
            throw new BusinessException(ResponseCode.MEETING_NAME_LIST_READ_ERROR);
        }
        return parse(readText(inputStream));
    }

    /**
     * 解析名单文本内容
     *
     * @param content 名单文本，可以是纯 JSON，也可以是包含 ```json 代码块的 Markdown
     * @return 名单条目列表
     */
    public static List<MeetingRosterItem> parse(String content) {
        if (ExcelCellUtils.isBlank(content)) {
            throw new BusinessException(ResponseCode.MEETING_NAME_LIST_EMPTY);
        }
        String text = content.startsWith(UTF8_BOM) ? content.substring(UTF8_BOM.length()) : content;

        List<MeetingRosterItem> items = tryParseArray(text);
        if (items == null) {
            // 整体解析失败，尝试剥离 Markdown 代码围栏
            Matcher matcher = CODE_FENCE_PATTERN.matcher(text);
            if (matcher.find()) {
                items = tryParseArray(matcher.group(1));
            }
        }
        if (items == null) {
            LOGGER.warn("共创会人员名单无法解析为JSON数组，内容前200字符：{}",
                    text.length() > 200 ? text.substring(0, 200) : text);
            throw new BusinessException(ResponseCode.MEETING_NAME_LIST_READ_ERROR);
        }
        if (items.isEmpty()) {
            throw new BusinessException(ResponseCode.MEETING_NAME_LIST_EMPTY);
        }

        List<MeetingRosterItem> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            MeetingRosterItem item = items.get(i);
            if (item == null || ExcelCellUtils.isBlank(item.getCode()) || ExcelCellUtils.isBlank(item.getName())) {
                // 单条脏数据不阻断整体统计，跳过并记日志
                LOGGER.warn("共创会人员名单第{}条缺少name或code字段，已跳过", i + 1);
                continue;
            }
            item.setCode(item.getCode().trim());
            item.setName(item.getName().trim());
            result.add(item);
        }
        if (result.isEmpty()) {
            LOGGER.warn("共创会人员名单解析后无有效条目");
            throw new BusinessException(ResponseCode.MEETING_NAME_LIST_EMPTY);
        }
        LOGGER.info("共创会人员名单解析完成，条数={}", result.size());
        return result;
    }

    /**
     * 读取名单文本内容（UTF-8）
     */
    private static String readText(InputStream inputStream) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int length;
            while ((length = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, length);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("读取共创会人员名单内容失败", e);
            throw new BusinessException(ResponseCode.MEETING_NAME_LIST_READ_ERROR, e);
        }
    }

    /**
     * 尝试按JSON数组解析；失败返回null而不抛异常，交由调用方决定后续策略
     */
    private static List<MeetingRosterItem> tryParseArray(String text) {
        if (ExcelCellUtils.isBlank(text)) {
            return null;
        }
        try {
            return JsonSupport.mapper().readValue(text.trim(), new TypeReference<List<MeetingRosterItem>>() {
            });
        } catch (Exception e) {
            LOGGER.debug("按JSON数组解析失败，将尝试剥离代码围栏", e);
            return null;
        }
    }
}
