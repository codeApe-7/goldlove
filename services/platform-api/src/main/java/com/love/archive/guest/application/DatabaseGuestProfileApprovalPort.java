package com.love.archive.guest.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.common.web.ApiException;
import com.love.archive.guest.domain.ProfileStatus;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.guest.persistence.GuestProfileMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DatabaseGuestProfileApprovalPort implements GuestProfileApprovalPort {

    private final GuestProfileMapper profileMapper;
    private final Clock clock;

    @Override
    public void markPending(long profileId, long revisionId, long expectedVersion) {
        int updated = profileMapper.update(Wrappers.<GuestProfileEntity>lambdaUpdate()
                .eq(GuestProfileEntity::getId, profileId)
                .eq(GuestProfileEntity::getVersion, expectedVersion)
                .isNull(GuestProfileEntity::getPendingRevisionId)
                .in(GuestProfileEntity::getStatus,
                        ProfileStatus.DRAFT,
                        ProfileStatus.CHANGES_REQUESTED,
                        ProfileStatus.APPROVED)
                .set(GuestProfileEntity::getPendingRevisionId, revisionId)
                .set(GuestProfileEntity::getStatus, ProfileStatus.PENDING_REVIEW)
                .set(GuestProfileEntity::getVersion, expectedVersion + 1)
                .set(GuestProfileEntity::getUpdatedAt, OffsetDateTime.now(clock)));
        if (updated != 1) {
            throw versionConflict();
        }
    }

    @Override
    public void approve(long profileId, long revisionId, long expectedProfileVersion) {
        int updated = profileMapper.update(Wrappers.<GuestProfileEntity>lambdaUpdate()
                .eq(GuestProfileEntity::getId, profileId)
                .eq(GuestProfileEntity::getVersion, expectedProfileVersion)
                .eq(GuestProfileEntity::getPendingRevisionId, revisionId)
                .eq(GuestProfileEntity::getStatus, ProfileStatus.PENDING_REVIEW)
                .set(GuestProfileEntity::getCurrentApprovedRevisionId, revisionId)
                .set(GuestProfileEntity::getPendingRevisionId, null)
                .set(GuestProfileEntity::getStatus, ProfileStatus.APPROVED)
                .set(GuestProfileEntity::getVersion, expectedProfileVersion + 1)
                .set(GuestProfileEntity::getUpdatedAt, OffsetDateTime.now(clock)));
        if (updated != 1) {
            throw versionConflict();
        }
    }

    @Override
    public void reject(long profileId, long revisionId, long expectedProfileVersion) {
        int updated = profileMapper.update(Wrappers.<GuestProfileEntity>lambdaUpdate()
                .eq(GuestProfileEntity::getId, profileId)
                .eq(GuestProfileEntity::getVersion, expectedProfileVersion)
                .eq(GuestProfileEntity::getPendingRevisionId, revisionId)
                .eq(GuestProfileEntity::getStatus, ProfileStatus.PENDING_REVIEW)
                .set(GuestProfileEntity::getPendingRevisionId, null)
                .set(GuestProfileEntity::getStatus, ProfileStatus.CHANGES_REQUESTED)
                .set(GuestProfileEntity::getVersion, expectedProfileVersion + 1)
                .set(GuestProfileEntity::getUpdatedAt, OffsetDateTime.now(clock)));
        if (updated != 1) {
            throw versionConflict();
        }
    }

    private static ApiException versionConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "PROFILE_VERSION_CONFLICT",
                "档案版本已发生变化，请刷新后重试");
    }
}
