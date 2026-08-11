package com.love.archive.identity.web;

import com.love.archive.common.web.ApiException;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.AuthLogics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class GuestStatusInterceptor implements HandlerInterceptor {

    private final AuthLogics authLogics;
    private final UserAccountMapper userAccountMapper;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        authLogics.guest().checkLogin();
        long accountId = authLogics.guest().getLoginIdAsLong();
        UserAccountEntity account = userAccountMapper.selectById(accountId);
        if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
            authLogics.guest().logout();
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "AUTH_ACCOUNT_INACTIVE",
                    "账号尚未激活或已停用");
        }
        return true;
    }
}
