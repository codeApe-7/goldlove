package com.love.archive.consent.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.love.archive.common.web.ApiException;
import com.love.archive.consent.persistence.AuthorizationRecordEntity;
import com.love.archive.consent.persistence.AuthorizationRecordMapper;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsentEligibility {

    private final AuthorizationRecordMapper authorizationRecordMapper;

    public void requireValid(long accountId, long authorizationDocumentId, OffsetDateTime now) {
        AuthorizationRecordEntity record = latestConsent(accountId, authorizationDocumentId);
        if (record == null) {
            throw new ApiException(HttpStatus.CONFLICT, "CONSENT_REQUIRED", "请先确认授权书");
        }
        if (!record.getExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONSENT_EXPIRED", "授权书确认已过期");
        }
    }

    private AuthorizationRecordEntity latestConsent(long accountId, long authorizationDocumentId) {
        Page<AuthorizationRecordEntity> page = authorizationRecordMapper.selectPage(
                new Page<>(1, 1, false),
                Wrappers.<AuthorizationRecordEntity>lambdaQuery()
                        .eq(AuthorizationRecordEntity::getUserAccountId, accountId)
                        .eq(AuthorizationRecordEntity::getAuthorizationDocumentId, authorizationDocumentId)
                        .eq(AuthorizationRecordEntity::getAccepted, true)
                        .orderByDesc(AuthorizationRecordEntity::getExpiresAt)
                        .orderByDesc(AuthorizationRecordEntity::getId));
        return page.getRecords().isEmpty() ? null : page.getRecords().getFirst();
    }
}
