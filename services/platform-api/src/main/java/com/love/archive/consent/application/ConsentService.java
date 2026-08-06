package com.love.archive.consent.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.security.SensitiveValueProtector;
import com.love.archive.common.web.ApiException;
import com.love.archive.consent.persistence.AuthorizationDocumentEntity;
import com.love.archive.consent.persistence.AuthorizationDocumentMapper;
import com.love.archive.consent.persistence.ConsentAccountLockMapper;
import com.love.archive.consent.persistence.AuthorizationRecordEntity;
import com.love.archive.consent.persistence.AuthorizationRecordMapper;
import com.love.archive.payment.application.PaymentAuthorizationEvidence;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsentService {

    private static final String DOCUMENT_CODE = "PAID_PROFILE_LIVE_CONTENT";
    private static final Set<String> SOURCE_PAGES = Set.of("guest-activation", "guest-profile", "guest-consent");

    private final AuthorizationDocumentQuery authorizationDocumentQuery;
    private final AuthorizationDocumentMapper authorizationDocumentMapper;
    private final ConsentAccountLockMapper consentAccountLockMapper;
    private final PaymentAuthorizationEvidence paymentAuthorizationEvidence;
    private final AuthorizationRecordMapper authorizationRecordMapper;
    private final SensitiveValueProtector sensitiveValueProtector;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Transactional
    public ConsentView accept(long accountId, ConsentEvidenceCommand command) {
        validateAccepted(command.accepted());
        validateSourcePage(command.sourcePage());
        consentAccountLockMapper.lockAccount(accountId);
        AuthorizationDocumentView document = authorizationDocumentQuery.requireVisibleToGuest(
                accountId, DOCUMENT_CODE, command.authorizationDocumentVersion());
        requirePaidDocument(accountId, document.id());
        OffsetDateTime now = OffsetDateTime.now(clock);
        ConsentView existing = findValid(accountId, document.id(), document.version(), now);
        if (existing != null) {
            return existing;
        }
        return appendEvidenceAndAudit(accountId, document, command, now, now.plusYears(1));
    }

    @Transactional(readOnly = true)
    public Optional<ConsentView> current(long accountId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return paymentAuthorizationEvidence.findPaidAuthorization(accountId)
                .map(payment -> findValid(
                        accountId,
                        payment.authorizationDocumentId(),
                        documentVersion(payment.authorizationDocumentId()),
                        now))
                .filter(java.util.Objects::nonNull);
    }

    private void requirePaidDocument(long accountId, long authorizationDocumentId) {
        boolean matchesPaidEvidence = paymentAuthorizationEvidence.findPaidAuthorization(accountId)
                .map(payment -> payment.authorizationDocumentId() == authorizationDocumentId)
                .orElse(false);
        if (!matchesPaidEvidence) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PREPAYMENT_AUTHORIZATION_EVIDENCE_MISSING",
                    "付款前展示的授权书版本不匹配");
        }
    }

    private ConsentView findValid(
            long accountId,
            long authorizationDocumentId,
            String authorizationDocumentVersion,
            OffsetDateTime now) {
        Page<AuthorizationRecordEntity> page = authorizationRecordMapper.selectPage(
                new Page<>(1, 1, false),
                Wrappers.<AuthorizationRecordEntity>lambdaQuery()
                        .eq(AuthorizationRecordEntity::getUserAccountId, accountId)
                        .eq(AuthorizationRecordEntity::getAuthorizationDocumentId, authorizationDocumentId)
                        .eq(AuthorizationRecordEntity::getAccepted, true)
                        .gt(AuthorizationRecordEntity::getExpiresAt, now)
                        .orderByDesc(AuthorizationRecordEntity::getExpiresAt)
                        .orderByDesc(AuthorizationRecordEntity::getId));
        return page.getRecords().isEmpty() ? null : toView(page.getRecords().getFirst(), authorizationDocumentVersion);
    }

    private String documentVersion(long authorizationDocumentId) {
        AuthorizationDocumentEntity document = authorizationDocumentMapper.selectById(authorizationDocumentId);
        if (document == null) {
            throw new IllegalStateException("付款授权书版本不存在");
        }
        return document.getVersion();
    }

    private ConsentView appendEvidenceAndAudit(
            long accountId,
            AuthorizationDocumentView document,
            ConsentEvidenceCommand command,
            OffsetDateTime acceptedAt,
            OffsetDateTime expiresAt) {
        AuthorizationRecordEntity record = new AuthorizationRecordEntity();
        record.setUserAccountId(accountId);
        record.setAuthorizationDocumentId(document.id());
        record.setAccepted(true);
        record.setAcceptedAt(acceptedAt);
        record.setEffectiveAt(acceptedAt);
        record.setExpiresAt(expiresAt);
        record.setSourcePage(command.sourcePage());
        record.setClientIpHmac(sensitiveValueProtector.hmac("consent:ip", normalize(command.clientIp())));
        record.setUserAgentSha256(sha256(normalize(command.userAgent())));
        record.setSessionReferenceHmac(sensitiveValueProtector.hmac("consent:session", normalize(command.sessionReference())));
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
        return toView(record, document.version());
    }

    private static void validateAccepted(boolean accepted) {
        if (!accepted) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONSENT_ACCEPTANCE_REQUIRED", "必须主动同意授权书");
        }
    }

    private static void validateSourcePage(String sourcePage) {
        if (!SOURCE_PAGES.contains(sourcePage)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONSENT_SOURCE_PAGE_INVALID", "授权来源页面不正确");
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.strip().replaceAll("\\s+", " ");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static ConsentView toView(AuthorizationRecordEntity record, String documentVersion) {
        return new ConsentView(
                record.getId(),
                record.getAuthorizationDocumentId(),
                documentVersion,
                record.getAcceptedAt(),
                record.getEffectiveAt(),
                record.getExpiresAt(),
                record.getSourcePage());
    }
}
