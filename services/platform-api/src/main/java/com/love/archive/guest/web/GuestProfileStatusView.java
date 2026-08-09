package com.love.archive.guest.web;

import com.love.archive.guest.application.GuestProfileDraftView;
import java.util.UUID;

public record GuestProfileStatusView(
        UUID profileNo,
        String status,
        Long version,
        Long pendingRevisionId,
        Long currentApprovedRevisionId) {

    static GuestProfileStatusView from(GuestProfileDraftView draft) {
        return new GuestProfileStatusView(
                draft.profileNo(), draft.status(), draft.version(),
                draft.pendingRevisionId(), draft.currentApprovedRevisionId());
    }
}
