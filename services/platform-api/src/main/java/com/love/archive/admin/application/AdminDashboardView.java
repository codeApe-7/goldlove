package com.love.archive.admin.application;

public record AdminDashboardView(
        long pendingReviews,
        long todayRegistrations,
        long todayReviews,
        long totalProfiles,
        long vipMembers,
        long svipMembers) {
}
