package com.jobtool.platform.service.devtool.impl;

import com.jobtool.platform.common.BusinessException;
import com.jobtool.platform.common.ResponseCode;
import com.jobtool.platform.constants.OkrStatisticsConstant;
import com.jobtool.platform.dto.devtool.OkrFilterOption;
import com.jobtool.platform.dto.devtool.OkrRosterItem;
import com.jobtool.platform.dto.devtool.OkrStatisticsListResponse;
import com.jobtool.platform.dto.devtool.OkrStatisticsRecord;
import com.jobtool.platform.dto.devtool.OkrStatisticsUploadResponse;
import com.jobtool.platform.service.devtool.OkrStatisticsService;
import com.jobtool.platform.support.devtool.OkrExcelParser;
import com.jobtool.platform.support.devtool.OkrNameListParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * OKR达成情况统计 Service 实现。
 *
 * <p>数据保存在内存中：本 Bean 是 Spring 单例，因此 {@link #snapshotRef} 即为多用户共享的全局数据。
 * 数据与元信息打包为不可变 {@link Snapshot}，上传成功时整体替换，
 * 保证读取方不会看到“数据已换、元信息未换”的中间态，也保证上传失败时旧数据不受影响。</p>
 *
 * <p>项目重启后数据清空，需要重新上传 Excel；本功能不落库，与迁移前行为一致。</p>
 *
 * @author wangpengfei
 */
@Service
@Slf4j
public class OkrStatisticsServiceImpl implements OkrStatisticsService {

    /** 筛选项字段名：姓名 */
    private static final String FIELD_NAME = "name";
    /** 筛选项字段名：工号 */
    private static final String FIELD_EMPLOYEE_NO = "employeeNo";
    /** 筛选项字段名：上月OKR达成复盘率 */
    private static final String FIELD_REVIEW_RATE = "reviewRate";
    /** 筛选项字段名：本月OKRI是否及时发布 */
    private static final String FIELD_OKR_PUBLISHED = "okrPublished";
    /** 筛选项字段名：增长曲线是否更新完成 */
    private static final String FIELD_GROWTH_CURVE = "growthCurveUpdated";
    /** 筛选项字段名：是否是经理及以上 */
    private static final String FIELD_MANAGER = "managerOrAbove";
    /** 筛选项字段名：O对齐率 */
    private static final String FIELD_O_ALIGN_RATE = "oAlignRate";

    /** 人员名单在 classpath 中的资源路径，直接写死在代码里（不走配置） */
    private static final String NAME_LIST_RESOURCE_PATH = "docs/okr/okrNameList.md";

    /** 名单来源展示前缀：classpath 资源用此前缀，本地文件直接展示绝对路径 */
    private static final String CLASSPATH_PREFIX = "classpath:";

    /**
     * 名单本地文件回退候选，按顺序取第一个存在的文件。
     *
     * <p>仅当 classpath 中取不到名单时使用（例如资源未被构建到 {@code target/classes}）。
     * 相对路径基于应用启动工作目录解析：在 job-tool-platform 目录启动时命中第 1 个，
     * 在 job-tool 仓库根目录启动时命中第 2 个。</p>
     */
    private static final String[] NAME_LIST_FILE_CANDIDATES = {
            "src/main/resources/" + NAME_LIST_RESOURCE_PATH,
            "job-tool-platform/src/main/resources/" + NAME_LIST_RESOURCE_PATH,
            "target/classes/" + NAME_LIST_RESOURCE_PATH
    };

    /** 上传文件为空时的提示 */
    private static final String EMPTY_FILE_MESSAGE = "上传文件为空，请重新选择";

    /** 全局内存数据 */
    private final AtomicReference<Snapshot> snapshotRef = new AtomicReference<>(Snapshot.empty());

    @Override
    public synchronized OkrStatisticsUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResponseCode.APPLICATION_PARAMETER_ERROR.getCode(), EMPTY_FILE_MESSAGE);
        }
        String originalFilename = file.getOriginalFilename();
        if (!isExcelFileName(originalFilename)) {
            log.warn("上传文件格式不支持：{}", originalFilename);
            throw new BusinessException(ResponseCode.OKR_FILE_TYPE_ERROR);
        }

        // 1. 解析达成Excel（先解析，失败则不动原有数据）
        List<OkrStatisticsRecord> okrRecords = parseExcel(file);

        // 2. 每次上传都重新读取人员名单，因为名单有可能已更新
        NameListContent nameList = readNameList();
        List<OkrRosterItem> rosterItems = nameList.items;

        // 3. 建立名单索引：工号为主键，姓名为兜底（需处理重名歧义）
        Map<String, OkrRosterItem> rosterByCode = new LinkedHashMap<>();
        Map<String, List<OkrRosterItem>> rosterByName = new HashMap<>();
        for (OkrRosterItem item : rosterItems) {
            String code = normalizeCode(item.getCode());
            if (rosterByCode.containsKey(code)) {
                log.warn("人员名单工号重复，保留第一条并忽略后续：code={}", code);
                continue;
            }
            rosterByCode.put(code, item);
            String name = normalizeName(item.getName());
            if (name != null) {
                rosterByName.computeIfAbsent(name, key -> new ArrayList<>()).add(item);
            }
        }

        // 4. 遍历达成Excel（保持Excel行顺序），逐个求交集
        Map<String, OkrStatisticsRecord> data = new LinkedHashMap<>();
        Set<String> matchedRosterCodes = new HashSet<>();
        int excludedNotInNameList = 0;
        int duplicateRow = 0;
        for (OkrStatisticsRecord record : okrRecords) {
            String employeeNo = normalizeCode(record.getEmployeeNo());
            OkrRosterItem matched = null;
            if (employeeNo != null && rosterByCode.containsKey(employeeNo)) {
                matched = rosterByCode.get(employeeNo);
            } else {
                // 工号未命中，用姓名兜底；姓名匹配到多条视为歧义，丢弃不猜测
                String name = normalizeName(record.getName());
                List<OkrRosterItem> candidates = name == null ? null : rosterByName.get(name);
                if (candidates != null && candidates.size() == 1) {
                    matched = candidates.get(0);
                } else if (candidates != null && candidates.size() > 1) {
                    log.warn("姓名匹配到多条人员名单记录，无法确认，已丢弃：name={}", record.getName());
                }
            }

            if (matched == null || employeeNo == null) {
                // 人员名单中没有（或缺少工号无法入库）-> 不保存
                excludedNotInNameList++;
                continue;
            }
            String matchedCode = normalizeCode(matched.getCode());
            if (data.containsKey(employeeNo) || matchedRosterCodes.contains(matchedCode)) {
                // 达成Excel工号重复、或同一名单人员匹配到多行：取第一条，后续忽略
                duplicateRow++;
                log.warn("重复行已忽略：employeeNo={}，rosterCode={}", employeeNo, matchedCode);
                continue;
            }

            matchedRosterCodes.add(matchedCode);
            record.setEmployeeNo(employeeNo);
            // 展示层归一化：O对齐率源值为空 -> 不适用，使其能与其他取值一样参与筛选合并
            if (isBlank(record.getAlignRate())) {
                record.setAlignRate(OkrStatisticsConstant.O_ALIGN_NOT_APPLICABLE);
            }
            data.put(employeeNo, record);
        }

        // 5. 统计“名单中有、达成Excel中没有”的人数（名单侧差集，同样不进入数据）
        Set<String> okrCodes = new HashSet<>();
        for (OkrStatisticsRecord record : okrRecords) {
            String code = normalizeCode(record.getEmployeeNo());
            if (code != null) {
                okrCodes.add(code);
            }
        }
        int excludedNotInOkr = 0;
        for (String rosterCode : new LinkedHashSet<>(rosterByCode.keySet())) {
            if (!okrCodes.contains(rosterCode)) {
                excludedNotInOkr++;
            }
        }
        if (duplicateRow > 0) {
            log.warn("本次上传存在{}条重复行，已忽略且不计入过滤统计", duplicateRow);
        }

        // 6. 构建元信息并整体替换内存数据
        OkrStatisticsUploadResponse meta = new OkrStatisticsUploadResponse();
        meta.setUploadedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        meta.setOkrFileName(originalFilename);
        meta.setNameListPath(nameList.location);
        meta.setOkrTotal(okrRecords.size());
        meta.setNameListTotal(rosterItems.size());
        meta.setMatchedTotal(data.size());
        meta.setExcludedNotInNameList(excludedNotInNameList);
        meta.setExcludedNotInOkr(excludedNotInOkr);
        snapshotRef.set(new Snapshot(data, meta));

        log.info("上传完成：达成{}条，名单{}条，匹配{}条，因不在名单过滤{}条，名单中未匹配{}条",
                meta.getOkrTotal(), meta.getNameListTotal(), meta.getMatchedTotal(),
                meta.getExcludedNotInNameList(), meta.getExcludedNotInOkr());
        return meta;
    }

    @Override
    public OkrStatisticsListResponse list() {
        Snapshot snapshot = snapshotRef.get();
        OkrStatisticsUploadResponse meta = snapshot.getMeta();

        OkrStatisticsListResponse response = new OkrStatisticsListResponse();
        if (meta != null) {
            response.setUploaded(true);
            response.setUploadedAt(meta.getUploadedAt());
            response.setOkrFileName(meta.getOkrFileName());
            response.setNameListPath(meta.getNameListPath());
            response.setOkrTotal(meta.getOkrTotal());
            response.setNameListTotal(meta.getNameListTotal());
            response.setMatchedTotal(meta.getMatchedTotal());
        }
        List<OkrStatisticsRecord> records = new ArrayList<>(snapshot.getData().values());
        response.setList(records);
        response.setTotal(records.size());
        response.setFilters(buildFilters(records));
        return response;
    }

    @Override
    public void clear() {
        snapshotRef.set(Snapshot.empty());
        log.info("OKR统计内存数据已清空");
    }

    /**
     * 构建7组筛选项：同值合并为一项并统计人数，label 为“值（个数）”。
     *
     * <p>排序规则：复盘率与O对齐率按数值降序（不适用/未知最后）；
     * 是否类字段固定 是→否；增长曲线固定 完成→未完成；姓名与工号按工号升序。</p>
     */
    private Map<String, List<OkrFilterOption>> buildFilters(List<OkrStatisticsRecord> records) {
        Map<String, List<OkrFilterOption>> filters = new LinkedHashMap<>();
        if (records.isEmpty()) {
            return filters;
        }

        // 姓名与工号：按工号升序排列，两个字段共用同一顺序
        List<OkrStatisticsRecord> byEmployeeNo = new ArrayList<>(records);
        byEmployeeNo.sort(Comparator.comparing(OkrStatisticsRecord::getEmployeeNo, this::compareString));
        filters.put(FIELD_NAME, toOptions(countBy(byEmployeeNo, OkrStatisticsRecord::getName)));
        filters.put(FIELD_EMPLOYEE_NO, toOptions(countBy(byEmployeeNo, OkrStatisticsRecord::getEmployeeNo)));

        // 复盘率、O对齐率：数值降序
        filters.put(FIELD_REVIEW_RATE, toOptions(sortByPercent(countBy(records, OkrStatisticsRecord::getReviewRate))));
        filters.put(FIELD_O_ALIGN_RATE, toOptions(sortByPercent(countBy(records, OkrStatisticsRecord::getAlignRate))));

        // 枚举类字段：固定业务顺序
        filters.put(FIELD_OKR_PUBLISHED, toOptions(sortByFixedOrder(
                countBy(records, OkrStatisticsRecord::getOkrPublished),
                OkrStatisticsConstant.YES, OkrStatisticsConstant.NO)));
        filters.put(FIELD_GROWTH_CURVE, toOptions(sortByFixedOrder(
                countBy(records, OkrStatisticsRecord::getGrowthCurveUpdated),
                OkrStatisticsConstant.DONE, OkrStatisticsConstant.UNDONE)));
        filters.put(FIELD_MANAGER, toOptions(sortByFixedOrder(
                countBy(records, OkrStatisticsRecord::getManagerOrAbove),
                OkrStatisticsConstant.YES, OkrStatisticsConstant.NO)));

        return filters;
    }

    /**
     * 按取值分组计数，保持首次出现顺序；空值统一归为“未知”
     */
    private Map<String, Integer> countBy(List<OkrStatisticsRecord> records,
                                         Function<OkrStatisticsRecord, String> getter) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (OkrStatisticsRecord record : records) {
            String value = getter.apply(record);
            if (isBlank(value)) {
                value = OkrStatisticsConstant.UNKNOWN_VALUE;
            }
            counts.merge(value, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * 百分比类排序：能解析的按数值降序，无法解析的（不适用/未知）固定排最后
     */
    private List<Map.Entry<String, Integer>> sortByPercent(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((e1, e2) -> {
            int rank1 = percentValue(e1.getKey()) < 0 ? 1 : 0;
            int rank2 = percentValue(e2.getKey()) < 0 ? 1 : 0;
            if (rank1 != rank2) {
                return rank1 - rank2;
            }
            int value1 = percentValue(e1.getKey());
            int value2 = percentValue(e2.getKey());
            if (value1 != value2) {
                return value2 - value1;
            }
            return compareString(e1.getKey(), e2.getKey());
        });
        return entries;
    }

    /**
     * 枚举类排序：按给定固定顺序，未识别的取值排最后
     */
    private List<Map.Entry<String, Integer>> sortByFixedOrder(Map<String, Integer> counts, String... order) {
        Map<String, Integer> rankMap = new HashMap<>();
        for (int i = 0; i < order.length; i++) {
            rankMap.put(order[i], i);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((e1, e2) -> {
            int rank1 = rankMap.getOrDefault(e1.getKey(), order.length);
            int rank2 = rankMap.getOrDefault(e2.getKey(), order.length);
            if (rank1 != rank2) {
                return rank1 - rank2;
            }
            return compareString(e1.getKey(), e2.getKey());
        });
        return entries;
    }

    private List<OkrFilterOption> toOptions(Map<String, Integer> counts) {
        return toOptions(new ArrayList<>(counts.entrySet()));
    }

    private List<OkrFilterOption> toOptions(List<Map.Entry<String, Integer>> entries) {
        List<OkrFilterOption> options = new ArrayList<>(entries.size());
        for (Map.Entry<String, Integer> entry : entries) {
            options.add(new OkrFilterOption(entry.getKey(), entry.getValue()));
        }
        return options;
    }

    /** 解析 "80%" -> 80；无法解析返回 -1 */
    private int percentValue(String value) {
        if (value == null) {
            return -1;
        }
        String text = value.trim();
        if (text.endsWith("%")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private List<OkrStatisticsRecord> parseExcel(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return OkrExcelParser.parse(inputStream);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("读取上传文件流失败", e);
            throw new BusinessException(ResponseCode.OKR_EXCEL_PARSE_ERROR, e);
        }
    }

    /**
     * 读取人员名单：优先取 classpath 中的资源，取不到时回退到本地文件。
     *
     * <p>名单位于 {@code src/main/resources/docs/okr/okrNameList.md}，构建后会进入 classpath，
     * 因此按资源读取与启动工作目录无关；本地未构建时再按 {@link #NAME_LIST_FILE_CANDIDATES} 找源码目录下的文件。</p>
     */
    private NameListContent readNameList() {
        InputStream inputStream = OkrStatisticsServiceImpl.class.getResourceAsStream("/" + NAME_LIST_RESOURCE_PATH);
        if (inputStream != null) {
            String location = CLASSPATH_PREFIX + NAME_LIST_RESOURCE_PATH;
            try {
                log.info("使用人员名单资源：{}", location);
                return new NameListContent(location, OkrNameListParser.parse(inputStream));
            } finally {
                closeQuietly(inputStream);
            }
        }

        for (String candidate : NAME_LIST_FILE_CANDIDATES) {
            File file = new File(candidate);
            if (file.exists() && file.isFile()) {
                log.warn("classpath 中未找到名单资源 {}，已回退使用本地文件：{}",
                        NAME_LIST_RESOURCE_PATH, file.getAbsolutePath());
                return new NameListContent(file.getAbsolutePath(), OkrNameListParser.parse(file));
            }
        }

        log.warn("未找到人员名单，classpath路径={}，本地候选={}，当前工作目录={}",
                NAME_LIST_RESOURCE_PATH, Arrays.toString(NAME_LIST_FILE_CANDIDATES),
                new File(".").getAbsolutePath());
        throw new BusinessException(ResponseCode.OKR_NAME_LIST_READ_ERROR);
    }

    private void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException e) {
            log.warn("关闭人员名单输入流失败", e);
        }
    }

    private boolean isExcelFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    /**
     * 工号规范化：去掉所有空白字符、兼容数字型单元格读出的 “60015465.0”、统一大小写
     */
    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String value = code.replaceAll("[\\s\\u3000]+", "");
        if (value.isEmpty()) {
            return null;
        }
        if (value.endsWith(".0") && value.length() > 2) {
            value = value.substring(0, value.length() - 2);
        }
        return value.toUpperCase();
    }

    /**
     * 姓名规范化：去空白、全角括号转半角、去掉（代）/（兼）等后缀，仅用于兜底匹配
     */
    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String value = name.replaceAll("[\\s\\u3000]+", "");
        if (value.isEmpty()) {
            return null;
        }
        value = value.replace('（', '(').replace('）', ')');
        return value.replaceAll("[(](代|兼)[)]$", "");
    }

    private int compareString(String s1, String s2) {
        if (s1 == null && s2 == null) {
            return 0;
        }
        if (s1 == null) {
            return 1;
        }
        if (s2 == null) {
            return -1;
        }
        return s1.compareTo(s2);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 名单读取结果：来源标识 + 解析后的条目
     */
    private static final class NameListContent {

        /** 来源标识：classpath 资源为 {@code classpath:docs/okr/okrNameList.md}，本地文件为绝对路径 */
        private final String location;

        private final List<OkrRosterItem> items;

        private NameListContent(String location, List<OkrRosterItem> items) {
            this.location = location;
            this.items = items;
        }
    }

    /**
     * 数据与元信息的不可变快照；meta 为 null 表示尚未上传过数据
     */
    private static final class Snapshot {

        private final Map<String, OkrStatisticsRecord> data;

        private final OkrStatisticsUploadResponse meta;

        private Snapshot(Map<String, OkrStatisticsRecord> data, OkrStatisticsUploadResponse meta) {
            this.data = data;
            this.meta = meta;
        }

        private static Snapshot empty() {
            return new Snapshot(Collections.emptyMap(), null);
        }

        private Map<String, OkrStatisticsRecord> getData() {
            return data;
        }

        private OkrStatisticsUploadResponse getMeta() {
            return meta;
        }
    }
}
