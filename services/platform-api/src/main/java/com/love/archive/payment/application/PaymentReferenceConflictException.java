package com.love.archive.payment.application;

public final class PaymentReferenceConflictException extends RuntimeException {

    public PaymentReferenceConflictException(Throwable cause) {
        super("Payment reference already exists", cause);
    }
}
