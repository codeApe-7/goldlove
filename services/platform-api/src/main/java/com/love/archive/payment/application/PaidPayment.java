package com.love.archive.payment.application;

import java.time.OffsetDateTime;

public record PaidPayment(
        Long userAccountId,
        String paymentReference,
        Long amountMinor,
        OffsetDateTime paidAt,
        Long operatorAdminId,
        Long presentedAuthorizationDocumentId,
        String note,
        OffsetDateTime createdAt) {
}
