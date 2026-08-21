package com.love.archive.admin.application;

public record AdminDashboardView(
        long totalAccounts,
        long todayRegistrations,
        long totalProfiles,
        long completedProfiles,
        long vipMembers,
        long svipMembers,
        long todayPaidAmountMinor) {
}
