package com.jobtool.platform.service.devtool.impl;

import com.jobtool.platform.common.BusinessException;
import com.jobtool.platform.common.ResponseCode;
import com.jobtool.platform.dto.devtool.MeetingCollectionRecord;
import com.jobtool.platform.dto.devtool.MeetingCollectionSummary;
import com.jobtool.platform.dto.devtool.MeetingRosterItem;
import com.jobtool.platform.dto.devtool.MeetingUserCollectionListResponse;
import com.jobtool.platform.dto.devtool.MeetingUserCollectionRequest;
import com.jobtool.platform.dto.devtool.MeetingUserRecordImportGroup;
import com.jobtool.platform.dto.devtool.MeetingUserRecordImportResponse;
import com.jobtool.platform.entity.meeting.MeetingActivity;
import com.jobtool.platform.entity.meeting.MeetingUserRealRecord;
import com.jobtool.platform.mapper.meeting.MeetingActivityMapper;
import com.jobtool.platform.mapper.meeting.MeetingUserRealRecordMapper;
import com.jobtool.platform.service.devtool.MeetingUserCollectionService;
import com.jobtool.platform.support.devtool.MeetingCollectionExcelWriter;
import com.jobtool.platform.support.devtool.MeetingNameListParser;
import com.jobtool.platform.support.devtool.MeetingUserRecordExcelParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 共创会人员参会情况统计 Service 实现。
 *
 * <p>数据来自数据库表 {@code meeting_user_real_record}，明细按人员名单取交集，
 * 战队汇总不施加名单过滤。计算口径与原功能一致。</p>
 *
 * @author wangpengfei
 */
@Service
@Slf4j
public class MeetingUserCollectionServiceImpl implements MeetingUserCollectionService {

    /** 人员名单在 classpath 中的资源路径，直接写死在代码里（不走配置） */
    private static final String NAME_LIST_RESOURCE_PATH = "docs/meeting/meetingNameList.md";

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

    /** 活动日期格式（接口回显用） */
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 日期时间格式（导入结果里的完成时间） */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 导入文件为空时的提示 */
    private static final String EMPTY_IMPORT_FILE_MESSAGE = "导入文件为空，请重新选择";

    private final MeetingUserRealRecordMapper meetingUserRealRecordMapper;

    private final MeetingActivityMapper meetingActivityMapper;

    public MeetingUserCollectionServiceImpl(MeetingUserRealRecordMapper meetingUserRealRecordMapper,
                                            MeetingActivityMapper meetingActivityMapper) {
        this.meetingUserRealRecordMapper = meetingUserRealRecordMapper;
        this.meetingActivityMapper = meetingActivityMapper;
    }

    @Override
    public MeetingUserCollectionListResponse list(MeetingUserCollectionRequest request) {
        return buildData(request).getResponse();
    }

    @Override
    public void export(MeetingUserCollectionRequest request, OutputStream outputStream) throws IOException {
        MeetingCollectionData data = buildData(request);
        MeetingUserCollectionListResponse response = data.getResponse();
        if (response.getList().isEmpty()) {
            throw new BusinessException(ResponseCode.MEETING_NO_EXPORT_DATA);
        }
        log.info("共创会人员统计-导出，activityId={}，明细={}条，战队={}",
                response.getActivityId(), response.getTotal(), response.getTeamName());
        MeetingCollectionExcelWriter.write(outputStream, response.getActivityId(), response.getMeetingName(),
                data.getMeetingDate(), response.getList(), response.getSummary());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeetingUserRecordImportResponse importExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResponseCode.APPLICATION_PARAMETER_ERROR.getCode(), EMPTY_IMPORT_FILE_MESSAGE);
        }
        String originalFilename = file.getOriginalFilename();
        if (!isExcelFileName(originalFilename)) {
            log.warn("导入文件格式不支持：{}", originalFilename);
            throw new BusinessException(ResponseCode.MEETING_IMPORT_FILE_TYPE_ERROR);
        }

        List<MeetingUserRealRecord> records;
        try (InputStream inputStream = file.getInputStream()) {
            records = MeetingUserRecordExcelParser.parse(inputStream);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("读取导入文件失败：{}", originalFilename, e);
            throw new BusinessException(ResponseCode.MEETING_IMPORT_EXCEL_PARSE_ERROR, e);
        }
        warnDuplicateUserCode(records);

        // 按「活动ID + 战队ID」分组，保持文件中首次出现的顺序：每组先清空历史数据，再统一插入
        Map<String, List<MeetingUserRealRecord>> grouped = new LinkedHashMap<>();
        for (MeetingUserRealRecord record : records) {
            String key = record.getActivityId() + "#" + record.getTeamId();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }

        List<MeetingUserRecordImportGroup> groups = new ArrayList<>();
        int deletedTotal = 0;
        for (List<MeetingUserRealRecord> groupRows : grouped.values()) {
            MeetingUserRealRecord first = groupRows.get(0);
            int deleted = meetingUserRealRecordMapper.deleteByActivityIdAndTeamId(
                    first.getActivityId(), first.getTeamId());
            deletedTotal += deleted;

            MeetingUserRecordImportGroup group = new MeetingUserRecordImportGroup();
            group.setActivityId(first.getActivityId());
            group.setTeamId(first.getTeamId());
            group.setTeamName(first.getTeamName());
            group.setRowCount(groupRows.size());
            group.setDeletedCount(deleted);
            groups.add(group);

            log.info("共创会数据导入-清空历史数据，activityId={}，teamId={}，teamName={}，删除={}条，本次导入={}条",
                    first.getActivityId(), first.getTeamId(), first.getTeamName(), deleted, groupRows.size());
        }

        int inserted = meetingUserRealRecordMapper.batchInsert(records);

        MeetingUserRecordImportResponse response = new MeetingUserRecordImportResponse();
        response.setFileName(originalFilename);
        response.setImportedAt(new SimpleDateFormat(DATE_TIME_PATTERN).format(new Date()));
        response.setTotal(records.size());
        response.setDeletedTotal(deletedTotal);
        response.setInsertedTotal(inserted);
        response.setGroups(groups);
        log.info("共创会数据导入完成，文件={}，清空={}条，插入={}条，涉及{}组活动+战队",
                originalFilename, deletedTotal, inserted, groups.size());
        return response;
    }

    /**
     * 查询 + 名单求交集 + 指标计算，查询与导出共用同一条链路，保证两者数据同源
     */
    private MeetingCollectionData buildData(MeetingUserCollectionRequest request) {
        validate(request);

        NameListContent nameList = readNameList();
        List<MeetingUserRealRecord> records =
                meetingUserRealRecordMapper.selectByActivityId(request.getActivityId());
        if (records == null) {
            records = new ArrayList<>();
        }

        Set<String> rosterCodes = new LinkedHashSet<>();
        for (MeetingRosterItem item : nameList.getItems()) {
            String code = normalizeCode(item.getCode());
            if (code != null) {
                rosterCodes.add(code);
            }
        }

        List<MeetingCollectionRecord> list = new ArrayList<>();
        Set<String> matchedCodes = new HashSet<>();
        int excludedNotInNameList = 0;
        for (MeetingUserRealRecord record : records) {
            String code = normalizeCode(record.getUserCode());
            if (code == null || !rosterCodes.contains(code)) {
                excludedNotInNameList++;
                continue;
            }
            matchedCodes.add(code);
            list.add(toRecord(record, request));
        }

        MeetingUserCollectionListResponse response = new MeetingUserCollectionListResponse();
        response.setActivityId(request.getActivityId());
        response.setAnswerScore(request.getAnswerScore());
        response.setAttendanceDuration(request.getAttendanceDuration());
        response.setNameListPath(nameList.getPath());
        response.setNameListTotal(nameList.getItems().size());
        response.setRecordTotal(records.size());
        response.setMatchedTotal(list.size());
        response.setExcludedNotInNameList(excludedNotInNameList);
        response.setExcludedNoRecord(Math.max(rosterCodes.size() - matchedCodes.size(), 0));
        response.setTotal(list.size());
        response.setList(list);

        // 汇总：战队取明细第一条记录（参考 SQL 注释：以上个SQL查出来第一个人的 team_name 为准）
        String teamName = list.isEmpty() ? null : list.get(0).getTeamName();
        response.setTeamName(teamName);
        if (teamName != null && !teamName.trim().isEmpty()) {
            MeetingCollectionSummary summary = meetingUserRealRecordMapper.selectTeamSummary(
                    request.getActivityId(), teamName, request.getAttendanceDuration(), request.getAnswerScore());
            if (summary != null) {
                // SQL 不再把参数当列返回，战队名在这里回填
                summary.setTeamName(teamName);
                summary.setScore(averageOfFourRates(summary.getJoinRate(), summary.getAnswerRate(),
                        summary.getAnswerAccuracy(), summary.getWatchRate()));
                response.setSummary(summary);
            }
        } else if (!list.isEmpty()) {
            log.warn("共创会人员统计-明细第一条记录的战队名为空，activityId={}，不输出汇总", request.getActivityId());
        }

        // 活动信息（共创会名称/活动日期），取不到不影响查询
        Date meetingDate = null;
        MeetingActivity activity = meetingActivityMapper.selectById(request.getActivityId());
        if (activity != null) {
            response.setMeetingName(activity.getMeetingName());
            meetingDate = activity.getMeetingBeginTime();
            if (meetingDate != null) {
                response.setMeetingDate(new SimpleDateFormat(DATE_PATTERN).format(meetingDate));
            }
        } else {
            log.info("共创会人员统计-meeting_activity 中不存在活动 {}，共创会名称与活动日期留空", request.getActivityId());
        }

        log.info("共创会人员统计-查询完成，activityId={}，记录={}条，名单={}条，匹配={}条，记录不在名单={}条，名单无记录={}条",
                request.getActivityId(), records.size(), nameList.getItems().size(), list.size(),
                excludedNotInNameList, response.getExcludedNoRecord());
        return new MeetingCollectionData(response, meetingDate);
    }

    /**
     * 参数校验：三个参数都必填，且两个分母必须大于 0
     */
    private void validate(MeetingUserCollectionRequest request) {
        if (request == null) {
            throw new BusinessException(ResponseCode.APPLICATION_PARAMETER_ERROR);
        }
        if (request.getActivityId() == null || request.getActivityId() <= 0L) {
            throw new BusinessException(ResponseCode.MEETING_ACTIVITY_ID_INVALID);
        }
        if (request.getAnswerScore() == null || request.getAnswerScore() <= 0D) {
            throw new BusinessException(ResponseCode.MEETING_ANSWER_SCORE_INVALID);
        }
        if (request.getAttendanceDuration() == null || request.getAttendanceDuration() <= 0L) {
            throw new BusinessException(ResponseCode.MEETING_ATTENDANCE_DURATION_INVALID);
        }
    }

    /**
     * 记录 -> 明细行，四舍五入统一按 HALF_UP，与 Excel ROUND 一致
     */
    private MeetingCollectionRecord toRecord(MeetingUserRealRecord entity, MeetingUserCollectionRequest request) {
        MeetingCollectionRecord record = new MeetingCollectionRecord();
        record.setUserCode(entity.getUserCode() == null ? null : String.valueOf(entity.getUserCode()));
        record.setUserName(entity.getUserName());
        record.setTeamName(entity.getTeamName());

        double answerScoreTotal = entity.getAnswerScoreTotal() == null ? 0D : entity.getAnswerScoreTotal();
        record.setAnswerScoreTotal(round(answerScoreTotal, 2));

        // 观看时长先按参会时长封顶
        long watchDuration = entity.getWatchDuration() == null ? 0L : entity.getWatchDuration();
        if (watchDuration > request.getAttendanceDuration()) {
            watchDuration = request.getAttendanceDuration();
        }
        record.setWatchDuration(watchDuration);

        double joinRate = round((entity.getJoinFlag() == null ? 0 : entity.getJoinFlag()) * 100D, 1);
        double answerRate = round((entity.getAnswerFlag() == null ? 0 : entity.getAnswerFlag()) * 100D, 1);
        double answerAccuracy = round(answerScoreTotal * 100D / request.getAnswerScore(), 1);
        double watchRate = round(watchDuration * 100D / request.getAttendanceDuration(), 1);
        record.setJoinRate(joinRate);
        record.setAnswerRate(answerRate);
        record.setAnswerAccuracy(answerAccuracy);
        record.setWatchRate(watchRate);
        record.setScore(averageOfFourRates(joinRate, answerRate, answerAccuracy, watchRate));
        return record;
    }

    /**
     * 分数 = ROUND((参会率 + 答题率 + 答题准确率 + 观看率) / 4, 1)，与样例文件的公式一致
     */
    private double averageOfFourRates(Double joinRate, Double answerRate, Double answerAccuracy, Double watchRate) {
        double sum = nullToZero(joinRate) + nullToZero(answerRate) + nullToZero(answerAccuracy) + nullToZero(watchRate);
        return round(sum / 4D, 1);
    }

    /**
     * 四舍五入到指定小数位，用 HALF_UP 与 Excel 的 ROUND 保持一致。
     *
     * <p>不要用 {@code Math.round}：98.25 这类恰好半值会得到 98.2，而 Excel 是 98.3。</p>
     */
    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private double nullToZero(Double value) {
        return value == null ? 0D : value;
    }

    /**
     * 读取人员名单：优先取 classpath 中的资源，取不到时回退到本地文件。
     *
     * <p>名单每次请求都重新读取，不使用任何缓存，名单文件更新后无需重启即可生效。</p>
     */
    private NameListContent readNameList() {
        InputStream inputStream = MeetingUserCollectionServiceImpl.class.getResourceAsStream("/" + NAME_LIST_RESOURCE_PATH);
        if (inputStream != null) {
            String location = CLASSPATH_PREFIX + NAME_LIST_RESOURCE_PATH;
            try {
                log.info("使用共创会人员名单资源：{}", location);
                return new NameListContent(location, MeetingNameListParser.parse(inputStream));
            } finally {
                closeQuietly(inputStream);
            }
        }

        for (String candidate : NAME_LIST_FILE_CANDIDATES) {
            File file = new File(candidate);
            if (file.exists() && file.isFile()) {
                log.warn("classpath 中未找到名单资源 {}，已回退使用本地文件：{}",
                        NAME_LIST_RESOURCE_PATH, file.getAbsolutePath());
                return new NameListContent(file.getAbsolutePath(), MeetingNameListParser.parse(file));
            }
        }

        log.warn("未找到共创会人员名单，classpath路径={}，本地候选={}，当前工作目录={}",
                NAME_LIST_RESOURCE_PATH, Arrays.toString(NAME_LIST_FILE_CANDIDATES),
                new File(".").getAbsolutePath());
        throw new BusinessException(ResponseCode.MEETING_NAME_LIST_READ_ERROR);
    }

    private void closeQuietly(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (IOException e) {
            log.warn("关闭共创会人员名单输入流失败", e);
        }
    }

    /**
     * 判断上传文件是否是 Excel（按扩展名）
     */
    private boolean isExcelFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    /**
     * 同一「活动ID + 战队ID」下工号重复时给出告警（不阻断导入，如实按文件落库）
     */
    private void warnDuplicateUserCode(List<MeetingUserRealRecord> records) {
        Map<String, Integer> counter = new HashMap<>();
        for (MeetingUserRealRecord record : records) {
            String key = record.getActivityId() + "#" + record.getTeamId() + "#" + record.getUserCode();
            counter.merge(key, 1, Integer::sum);
        }
        int duplicated = 0;
        for (Map.Entry<String, Integer> entry : counter.entrySet()) {
            if (entry.getValue() > 1) {
                duplicated++;
                log.warn("共创会数据导入-同一活动与战队下工号重复，key={}，出现次数={}",
                        entry.getKey(), entry.getValue());
            }
        }
        if (duplicated > 0) {
            log.warn("共创会数据导入-共 {} 个「活动+战队+工号」重复，将按文件原样落库", duplicated);
        }
    }

    /**
     * 工号规范化：去掉所有空白字符（含全角空格）、兼容 “60015465.0” 这类小数尾、去掉前导零。
     *
     * <p>两侧（名单与数据库）都要做，避免因书写形态不同而漏匹配。</p>
     */
    private String normalizeCode(Object code) {
        if (code == null) {
            return null;
        }
        String value = String.valueOf(code).replaceAll("[\\s\\u3000]+", "");
        if (value.isEmpty()) {
            return null;
        }
        if (value.endsWith(".0")) {
            value = value.substring(0, value.length() - 2);
        }
        // 去掉前导零（"020120148" -> "20120148"），全为 0 时保留
        int index = 0;
        while (index < value.length() - 1 && value.charAt(index) == '0') {
            index++;
        }
        value = value.substring(index);
        return value.isEmpty() ? null : value;
    }

    /**
     * 名单内容 + 实际来源
     */
    private static final class NameListContent {

        private final String path;

        private final List<MeetingRosterItem> items;

        private NameListContent(String path, List<MeetingRosterItem> items) {
            this.path = path;
            this.items = items;
        }

        private String getPath() {
            return path;
        }

        private List<MeetingRosterItem> getItems() {
            return items;
        }
    }

    /**
     * 查询结果 + 活动日期（导出需要原始 Date，接口回显用字符串）
     */
    private static final class MeetingCollectionData {

        private final MeetingUserCollectionListResponse response;

        private final Date meetingDate;

        private MeetingCollectionData(MeetingUserCollectionListResponse response, Date meetingDate) {
            this.response = response;
            this.meetingDate = meetingDate;
        }

        private MeetingUserCollectionListResponse getResponse() {
            return response;
        }

        private Date getMeetingDate() {
            return meetingDate;
        }
    }
}
