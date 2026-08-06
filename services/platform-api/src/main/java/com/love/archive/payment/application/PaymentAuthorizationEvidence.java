package com.love.archive.payment.application;

import java.util.Optional;

public interface PaymentAuthorizationEvidence {

    Optional<PresentedAuthorization> findPaidAuthorization(long accountId);
}
