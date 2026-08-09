package com.love.archive.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.love.archive.admin.persistence.AdminDashboardMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminDashboardServiceTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2030-07-01T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void computesShanghaiDayBoundsAndReturnsView() {
        AdminDashboardMapper mapper = mock(AdminDashboardMapper.class);
        when(mapper.loadStats(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(new AdminDashboardView(2, 3, 1, 9));
        AdminDashboardService service = new AdminDashboardService(mapper, FIXED);

        AdminDashboardView view = service.stats();

        assertThat(view.pendingReviews()).isEqualTo(2);
        assertThat(view.todayRegistrations()).isEqualTo(3);
        assertThat(view.todayReviews()).isEqualTo(1);
        assertThat(view.totalProfiles()).isEqualTo(9);
        ArgumentCaptor<OffsetDateTime> start = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> end = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(mapper).loadStats(start.capture(), end.capture());
        assertThat(start.getValue().toInstant())
                .isEqualTo(Instant.parse("2030-06-30T16:00:00Z"));
        assertThat(end.getValue().toInstant())
                .isEqualTo(Instant.parse("2030-07-01T16:00:00Z"));
    }
}
