package com.love.archive.consent.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.common.web.ApiException;
import com.love.archive.consent.domain.AuthorizationDocumentStatus;
import com.love.archive.consent.persistence.AuthorizationDocumentEntity;
import com.love.archive.consent.persistence.AuthorizationDocumentMapper;
import com.love.archive.payment.application.PaymentAuthorizationEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class DatabaseAuthorizationDocumentQuery implements AuthorizationDocumentQuery {

    private final AuthorizationDocumentMapper authorizationDocumentMapper;
    private final PaymentAuthorizationEvidence paymentAuthorizationEvidence;

    @Override
    public AuthorizationDocumentView current(String documentCode) {
        AuthorizationDocumentEntity document = authorizationDocumentMapper.selectOne(
                Wrappers.<AuthorizationDocumentEntity>lambdaQuery()
                        .eq(AuthorizationDocumentEntity::getDocumentCode, documentCode)
                        .eq(AuthorizationDocumentEntity::getStatus, AuthorizationDocumentStatus.ACTIVE));
        if (document == null) {
            throw documentNotFound();
        }
        return toView(document);
    }

    @Override
    public AuthorizationDocumentView requireActive(String documentCode, String version) {
        AuthorizationDocumentEntity document = find(documentCode, version);
        if (document == null) {
            throw documentNotFound();
        }
        if (document.getStatus() != AuthorizationDocumentStatus.ACTIVE) {
            throw documentNotActive();
        }
        return toView(document);
    }

    @Override
    public AuthorizationDocumentView requireVisibleToGuest(long accountId, String documentCode, String version) {
        AuthorizationDocumentEntity document = find(documentCode, version);
        if (document == null) {
            throw documentNotFound();
        }
        if (document.getStatus() == AuthorizationDocumentStatus.ACTIVE) {
            return toView(document);
        }
        boolean referencesDocument = document.getStatus() == AuthorizationDocumentStatus.RETIRED
                && paymentAuthorizationEvidence.findPaidAuthorization(accountId)
                        .map(presented -> presented.authorizationDocumentId() == document.getId())
                        .orElse(false);
        if (!referencesDocument) {
            throw documentNotActive();
        }
        return toView(document);
    }

    private AuthorizationDocumentEntity find(String documentCode, String version) {
        return authorizationDocumentMapper.selectOne(
                Wrappers.<AuthorizationDocumentEntity>lambdaQuery()
                        .eq(AuthorizationDocumentEntity::getDocumentCode, documentCode)
                        .eq(AuthorizationDocumentEntity::getVersion, version));
    }

    private static AuthorizationDocumentView toView(AuthorizationDocumentEntity document) {
        return new AuthorizationDocumentView(
                document.getId(),
                document.getDocumentCode(),
                document.getVersion(),
                document.getTitle(),
                document.getContent(),
                document.getContentSha256(),
                document.getEffectiveAt());
    }

    private static ApiException documentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "AUTHORIZATION_DOCUMENT_NOT_FOUND", "授权书版本不存在");
    }

    private static ApiException documentNotActive() {
        return new ApiException(HttpStatus.CONFLICT, "AUTHORIZATION_DOCUMENT_NOT_ACTIVE", "授权书版本当前不可用");
    }
}
