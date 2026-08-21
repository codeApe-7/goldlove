package com.love.archive.guest.web;

import com.love.archive.guest.application.GuestProfileDraftView;
import java.util.List;
import java.util.UUID;

public record GuestProfileStatusView(
        UUID profileNo,
        String status,
        Long version,
        List<String> missingRequiredFieldCodes) {

    static GuestProfileStatusView from(GuestProfileDraftView draft) {
        return new GuestProfileStatusView(
                draft.profileNo(), draft.status(), draft.version(),
                draft.missingRequiredFieldCodes());
    }
}
