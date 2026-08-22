package com.love.archive.admin.application;

import com.love.archive.admin.persistence.AdminProfileQueryMapper;
import com.love.archive.admin.persistence.query.AdminProfileDetailRow;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.web.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 档案 CSV 导出。导出的是完整字段（含手机号、微信号、抖音号），每次导出都追加一条审计记录。
 *
 * <p>审计元数据里只记「用了关键词筛选」这个事实，不记关键词本身——关键词往往就是手机号片段，
 * 把它复制进 audit_log 等于让同一份 PII 多一处落点，而追溯需要的信息（谁、何时、导了多少条、
 * 什么范围）并不依赖它。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminProfileExportService {

    /** 单次导出上限。没有上限的「导出当前筛选全量」等于给了一个能拖垮服务的入口。 */
    public static final int MAX_EXPORT_ROWS = 5000;

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> HEADERS = List.of(
            "档案编号", "手机号", "账号状态", "完成度", "会员等级", "累计付费(元)",
            "性别", "出生日期", "年龄", "身高(cm)", "学历", "职业", "年薪",
            "所在地区", "微信号", "抖音号", "抖音昵称", "抖音主页",
            "创建时间", "更新时间");

    private final AdminProfileQueryMapper profileQueryMapper;
    private final AuditTrail auditTrail;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public ProfileExportFile export(
            AdminProfileFilter filter, List<Long> ids, long adminId, String requestId) {
        if (ids != null && ids.size() > MAX_EXPORT_ROWS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROFILE_EXPORT_TOO_LARGE",
                    "单次最多导出 " + MAX_EXPORT_ROWS + " 条");
        }
        List<AdminProfileDetailRow> rows =
                profileQueryMapper.export(filter, ids, MAX_EXPORT_ROWS);
        OffsetDateTime now = OffsetDateTime.now(clock);

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                "PROFILE_EXPORTED",
                "GUEST_PROFILE",
                null,
                requestId,
                exportMetadata(filter, ids, rows.size()),
                now));

        return new ProfileExportFile(
                "profiles-" + TIMESTAMP.format(now.atZoneSameInstant(ZoneOffset.UTC))
                        .replace(':', '-').replace(' ', '_') + ".csv",
                toCsv(rows));
    }

    private String toCsv(List<AdminProfileDetailRow> rows) {
        // Excel 只有见到 BOM 才把 CSV 当 UTF-8 读，否则中文全是乱码。
        StringBuilder csv = new StringBuilder("﻿");
        csv.append(String.join(",", HEADERS)).append("\r\n");
        LocalDate today = LocalDate.now(clock);
        for (AdminProfileDetailRow row : rows) {
            appendRow(csv, row, today);
        }
        return csv.toString();
    }

    private static void appendRow(
            StringBuilder csv, AdminProfileDetailRow row, LocalDate today) {
        List<String> cells = List.of(
                text(row.getProfileNo()),
                text(row.getPhone()),
                text(row.getAccountStatus()),
                text(row.getStatus()),
                text(row.getMembershipTier()),
                row.getMembershipCreditMinor() == null
                        ? "0.00"
                        : String.format("%.2f", row.getMembershipCreditMinor() / 100.0),
                text(row.getGender()),
                text(row.getBirthDate()),
                age(row.getBirthDate(), today),
                text(row.getHeightCm()),
                text(row.getEducation()),
                text(row.getOccupation()),
                text(row.getIncomeRange()),
                text(row.getCity()),
                text(row.getWechatId()),
                text(row.getDouyinId()),
                text(row.getDouyinNickname()),
                text(row.getDouyinProfileUrl()),
                timestamp(row.getCreatedAt()),
                timestamp(row.getUpdatedAt()));
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(escape(cells.get(index)));
        }
        csv.append("\r\n");
    }

    private static String age(LocalDate birthDate, LocalDate today) {
        if (birthDate == null || birthDate.isAfter(today)) {
            return "";
        }
        return String.valueOf(Period.between(birthDate, today).getYears());
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String timestamp(OffsetDateTime value) {
        return value == null ? "" : TIMESTAMP.format(value.atZoneSameInstant(ZoneOffset.UTC));
    }

    /**
     * CSV 转义。除了引号与分隔符，还要挡住公式注入：
     * 以 = + - @ 或制表/回车开头的单元格会被 Excel 当公式执行，前置单引号让它保持文本。
     */
    private static String escape(String value) {
        String cell = value;
        if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) {
            cell = "'" + cell;
        }
        if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0
                || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
            return '"' + cell.replace("\"", "\"\"") + '"';
        }
        return cell;
    }

    private String exportMetadata(AdminProfileFilter filter, List<Long> ids, int exported) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("exported", exported);
        metadata.put("scope", ids == null || ids.isEmpty() ? "FILTERED" : "SELECTION");
        metadata.put("keywordUsed", filter.keyword() != null);
        metadata.put("status", filter.status());
        metadata.put("accountStatus", filter.accountStatus());
        metadata.put("membershipTier", filter.membershipTier());
        metadata.put("paidOnly", filter.paidOnly());
        metadata.put("city", filter.city());
        metadata.put("createdFrom", filter.createdFrom() == null ? null : filter.createdFrom().toString());
        metadata.put("createdTo", filter.createdTo() == null ? null : filter.createdTo().toString());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException error) {
            // 审计元数据写不出来不该让导出失败，但也不能悄悄丢掉这次导出的痕迹。
            return "{\"exported\":" + exported + ",\"metadataSerializationFailed\":true}";
        }
    }
}
