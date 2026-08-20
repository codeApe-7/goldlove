package com.love.archive.consent.application;

import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.web.ApiException;
import com.love.archive.consent.persistence.AuthorizationRecordEntity;
import com.love.archive.consent.persistence.AuthorizationRecordMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 授权书同意记录。注册时勾选即写入一条，只追加不可改。
 * 记录仍带一年期限用于留痕，但不再作为访问档案的前置条件——注册是唯一的确认时点。
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    public static final String DOCUMENT_CODE = "PROFILE_LIVE_CONTENT";
    private static final String SOURCE_PAGE = "guest-register";

    private final AuthorizationDocumentQuery authorizationDocumentQuery;
    private final AuthorizationRecordMapper authorizationRecordMapper;
    private final AuditTrail auditTrail;
    private final Clock clock;

    /**
     * 在注册事务内记录同意。调用方必须已经建好账号，因为记录带外键。
     *
     * @return 同意记录主键
     */
    @Transactional
    public long recordRegistrationConsent(long accountId, ConsentEvidenceCommand command) {
        if (!command.accepted()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONSENT_ACCEPTANCE_REQUIRED", "必须主动同意授权书");
        }
        AuthorizationDocumentView document = command.authorizationDocumentVersion() == null
                        || command.authorizationDocumentVersion().isBlank()
                ? authorizationDocumentQuery.current(DOCUMENT_CODE)
                : authorizationDocumentQuery.requireActive(
                        DOCUMENT_CODE, command.authorizationDocumentVersion());

        OffsetDateTime acceptedAt = OffsetDateTime.now(clock);
        AuthorizationRecordEntity record = new AuthorizationRecordEntity();
        record.setUserAccountId(accountId);
        record.setAuthorizationDocumentId(document.id());
        record.setAccepted(true);
        record.setAcceptedAt(acceptedAt);
        record.setEffectiveAt(acceptedAt);
        record.setExpiresAt(acceptedAt.plusYears(1));
        record.setSourcePage(SOURCE_PAGE);
        record.setClientIp(trim(command.clientIp(), 45));
        record.setUserAgent(trim(command.userAgent(), 500));
        record.setSessionReference(trim(command.sessionReference(), 64));
        authorizationRecordMapper.insert(record);

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.GUEST,
                accountId,
                "CONSENT_ACCEPTED",
                "AUTHORIZATION_RECORD",
                record.getId(),
                null,
                "{\"authorizationDocumentVersion\":\"" + document.version() + "\"}",
                acceptedAt));
        return record.getId();
    }

    private static String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
