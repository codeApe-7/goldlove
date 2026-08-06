package com.love.archive.identity.web;

import com.love.archive.identity.domain.AccountStatus;

public record GuestSessionView(Long accountId, AccountStatus status) {
}
