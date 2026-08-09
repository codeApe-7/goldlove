package com.love.archive.guest.application;

public interface GuestProfileApprovalPort {

    void markPending(long profileId, long revisionId, long expectedVersion);

    void approve(long profileId, long revisionId, long expectedProfileVersion);

    void reject(long profileId, long revisionId, long expectedProfileVersion);
}
