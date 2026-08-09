package com.love.archive.admin.application;

import com.love.archive.admin.persistence.AdminDashboardMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final AdminDashboardMapper dashboardMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminDashboardView stats() {
        ZonedDateTime now = OffsetDateTime.now(clock).atZoneSameInstant(BUSINESS_ZONE);
        OffsetDateTime dayStart = now.toLocalDate()
                .atStartOfDay(BUSINESS_ZONE)
                .toOffsetDateTime();
        OffsetDateTime dayEnd = dayStart.plusDays(1);
        return dashboardMapper.loadStats(dayStart, dayEnd);
    }
}
