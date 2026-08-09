package com.love.archive.identity.application;

import com.love.archive.common.web.ApiException;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestAccountStatusQuery {

    private final UserAccountMapper userAccountMapper;

    @Transactional(readOnly = true)
    public void requireActive(long accountId) {
        UserAccountEntity account = userAccountMapper.selectById(accountId);
        if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "AUTH_ACCOUNT_INACTIVE",
                    "账号尚未激活或已停用");
        }
    }
}
