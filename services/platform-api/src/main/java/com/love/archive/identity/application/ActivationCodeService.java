package com.love.archive.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.web.ApiException;
import com.love.archive.identity.domain.ActivationCodeStatus;
import com.love.archive.identity.domain.MembershipTier;
import com.love.archive.identity.domain.PhoneNormalizer;
import com.love.archive.identity.persistence.ActivationCodeEntity;
import com.love.archive.identity.persistence.ActivationCodeMapper;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.ActivationCodeGenerator;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 激活码：管理员生成时就绑定手机号，只有该手机号的账号能兑换。
 * 码以明文存库——管理员必须能复读并分发给对应用户。
 */
@Service
@RequiredArgsConstructor
public class ActivationCodeService {

    private static final int GENERATE_RETRIES = 5;

    private final ActivationCodeMapper activationCodeMapper;
    private final UserAccountMapper userAccountMapper;
    private final MembershipService membershipService;
    private final ActivationCodeGenerator codeGenerator;
    private final PhoneNormalizer phoneNormalizer;
    private final AuditTrail auditTrail;

    @Transactional
    public ActivationCodeView generate(GenerateActivationCodeCommand command) {
        String phone = normalizePhone(command.boundPhone());
        MembershipTier tier = command.grantedTier() == null ? MembershipTier.VIP : command.grantedTier();
        if (tier == MembershipTier.FREE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "ACTIVATION_CODE_TIER_INVALID", "激活码只能授予 VIP 或 SVIP");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ActivationCodeEntity code = new ActivationCodeEntity();
        code.setBoundPhone(phone);
        code.setGrantedTier(tier);
        code.setStatus(ActivationCodeStatus.UNUSED);
        code.setCreatedByAdminId(command.adminId());
        code.setNote(command.note());
        code.setCreatedAt(now);
        code.setUpdatedAt(now);
        insertWithUniqueCode(code);

        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                command.adminId(),
                "ACTIVATION_CODE_GENERATED",
                "ACTIVATION_CODE",
                code.getId(),
                command.requestId(),
                "{\"grantedTier\":\"" + tier.databaseValue() + "\"}",
                now));
        return ActivationCodeView.of(code, phoneRegistered(phone));
    }

    @Transactional
    public MembershipView redeem(long accountId, String rawCode, String requestId) {
        String normalized = ActivationCodeGenerator.normalize(rawCode);
        if (normalized.isEmpty()) {
            throw notFound();
        }
        ActivationCodeEntity code = activationCodeMapper.selectOne(
                Wrappers.<ActivationCodeEntity>lambdaQuery()
                        .eq(ActivationCodeEntity::getCode, normalized)
                        .last("FOR UPDATE"));
        if (code == null) {
            throw notFound();
        }
        if (code.getStatus() == ActivationCodeStatus.USED) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVATION_CODE_USED", "激活码已被使用");
        }
        if (code.getStatus() == ActivationCodeStatus.REVOKED) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVATION_CODE_REVOKED", "激活码已作废");
        }

        UserAccountEntity account = userAccountMapper.selectById(accountId);
        if (account == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在");
        }
        if (!code.getBoundPhone().equals(account.getPhone())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "ACTIVATION_CODE_PHONE_MISMATCH", "该激活码不属于当前手机号");
        }

        OffsetDateTime now = OffsetDateTime.now();
        int updated = activationCodeMapper.update(
                Wrappers.<ActivationCodeEntity>lambdaUpdate()
                        .eq(ActivationCodeEntity::getId, code.getId())
                        .eq(ActivationCodeEntity::getStatus, ActivationCodeStatus.UNUSED)
                        .set(ActivationCodeEntity::getStatus, ActivationCodeStatus.USED)
                        .set(ActivationCodeEntity::getRedeemedByAccountId, accountId)
                        .set(ActivationCodeEntity::getRedeemedAt, now)
                        .set(ActivationCodeEntity::getUpdatedAt, now));
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVATION_CODE_USED", "激活码已被使用");
        }

        MembershipView membership = membershipService.grantTier(accountId, code.getGrantedTier());
        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.GUEST,
                accountId,
                "ACTIVATION_CODE_REDEEMED",
                "ACTIVATION_CODE",
                code.getId(),
                requestId,
                "{\"grantedTier\":\"" + code.getGrantedTier().databaseValue() + "\"}",
                now));
        return membership;
    }

    @Transactional
    public void revoke(long codeId, long adminId, String requestId) {
        OffsetDateTime now = OffsetDateTime.now();
        int updated = activationCodeMapper.update(
                Wrappers.<ActivationCodeEntity>lambdaUpdate()
                        .eq(ActivationCodeEntity::getId, codeId)
                        .eq(ActivationCodeEntity::getStatus, ActivationCodeStatus.UNUSED)
                        .set(ActivationCodeEntity::getStatus, ActivationCodeStatus.REVOKED)
                        .set(ActivationCodeEntity::getUpdatedAt, now));
        if (updated != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "ACTIVATION_CODE_NOT_REVOCABLE", "只有未使用的激活码可以作废");
        }
        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.ADMIN,
                adminId,
                "ACTIVATION_CODE_REVOKED",
                "ACTIVATION_CODE",
                codeId,
                requestId,
                "{}",
                now));
    }

    private void insertWithUniqueCode(ActivationCodeEntity code) {
        for (int attempt = 1; attempt <= GENERATE_RETRIES; attempt++) {
            code.setCode(codeGenerator.generate());
            try {
                activationCodeMapper.insert(code);
                return;
            } catch (DataIntegrityViolationException exception) {
                boolean collision = exception.getMessage() != null
                        && exception.getMessage().contains("uq_activation_code");
                if (!collision || attempt == GENERATE_RETRIES) {
                    throw exception;
                }
            }
        }
    }

    private boolean phoneRegistered(String phone) {
        return userAccountMapper.selectCount(Wrappers.<UserAccountEntity>lambdaQuery()
                .eq(UserAccountEntity::getPhone, phone)) > 0;
    }

    private String normalizePhone(String rawPhone) {
        try {
            return phoneNormalizer.normalize(rawPhone);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "手机号格式不正确");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ACTIVATION_CODE_NOT_FOUND", "激活码不存在");
    }
}
