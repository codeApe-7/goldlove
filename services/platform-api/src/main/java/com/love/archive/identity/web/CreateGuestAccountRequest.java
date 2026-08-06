package com.love.archive.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CreateGuestAccountRequest(
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+") String paymentReference,
        @NotNull @Positive Long amountMinor,
        @NotNull @PastOrPresent OffsetDateTime paidAt,
        @Size(max = 500) String note) {
}
