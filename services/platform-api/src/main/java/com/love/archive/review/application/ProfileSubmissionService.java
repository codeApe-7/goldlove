package com.love.archive.review.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.audit.application.AuditEvent;
import com.love.archive.audit.application.AuditTrail;
import com.love.archive.common.security.SensitiveValueProtector;
import com.love.archive.common.web.ApiException;
import com.love.archive.consent.application.ConsentEligibility;
import com.love.archive.guest.domain.PhotoCategory;
import com.love.archive.guest.application.GuestProfileApprovalPort;
import com.love.archive.guest.application.GuestProfileSnapshot;
import com.love.archive.guest.application.GuestProfileSnapshotProvider;
import com.love.archive.payment.application.PaymentAuthorizationEvidence;
import com.love.archive.payment.application.PresentedAuthorization;
import com.love.archive.review.domain.RevisionStatus;
import com.love.archive.review.persistence.ProfileRevisionEntity;
import com.love.archive.review.persistence.ProfileRevisionFieldValueEntity;
import com.love.archive.review.persistence.ProfileRevisionFieldValueMapper;
import com.love.archive.review.persistence.ProfileRevisionMapper;
import com.love.archive.review.persistence.ProfileRevisionPhotoEntity;
import com.love.archive.review.persistence.ProfileRevisionPhotoMapper;
import com.love.archive.storage.application.ObjectStorageService;
import java.time.Duration;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestOutputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProfileSubmissionService {

    private static final String IDEMPOTENCY_DOMAIN = "profile:submission";

    private final GuestProfileSnapshotProvider snapshotProvider;
    private final GuestProfileApprovalPort profileApprovalPort;
    private final PaymentAuthorizationEvidence paymentAuthorizationEvidence;
    private final ConsentEligibility consentEligibility;
    private final SensitiveValueProtector protector;
    private final CanonicalSnapshotHasher canonicalSnapshotHasher;
    private final ProfileRevisionMapper revisionMapper;
    private final ProfileRevisionFieldValueMapper revisionFieldMapper;
    private final ProfileRevisionPhotoMapper revisionPhotoMapper;
    private final AuditTrail auditTrail;
    private final ObjectStorageService storageService;
    private final Clock clock;

    @Transactional
    public ProfileRevisionView submit(
            long accountId,
            String idempotencyKey,
            String requestId) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 128) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key 请求头不能为空且不能超过 128 个字符");
        }

        GuestProfileSnapshot snapshot = snapshotProvider.lockAndValidate(accountId);
        String keyHmac = protector.hmac(IDEMPOTENCY_DOMAIN, idempotencyKey);
        String payloadSha256 = canonicalSnapshotHasher.sha256(snapshot);
        ProfileRevisionView replay = resolveReplay(
                snapshot.profileId(), keyHmac, payloadSha256);
        if (replay != null) {
            return replay;
        }

        PresentedAuthorization payment = requirePaymentEvidence(accountId);
        consentEligibility.requireValid(
                accountId,
                payment.authorizationDocumentId(),
                OffsetDateTime.now(clock));
        if (snapshot.pendingRevisionId() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROFILE_REVIEW_IN_PROGRESS",
                    "档案正在审核，请勿重复提交");
        }
        return insertSnapshotMarkPendingAndAudit(
                snapshot, keyHmac, payloadSha256, requestId);
    }

    @Transactional(readOnly = true)
    public ProfileRevisionView getOwned(long accountId, long revisionId) {
        ProfileRevisionEntity revision = revisionMapper.selectOwnedRevision(accountId, revisionId);
        if (revision == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PROFILE_REVISION_NOT_FOUND",
                    "档案版本不存在");
        }
        return toView(revision);
    }

    private ProfileRevisionView resolveReplay(
            long profileId,
            String keyHmac,
            String payloadSha256) {
        ProfileRevisionEntity existing = revisionMapper.selectOne(
                Wrappers.<ProfileRevisionEntity>lambdaQuery()
                        .eq(ProfileRevisionEntity::getGuestProfileId, profileId)
                        .eq(ProfileRevisionEntity::getSubmissionKeyHmac, keyHmac));
        if (existing == null) {
            return null;
        }
        if (!existing.getRequestPayloadSha256().equals(payloadSha256)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key 已用于不同的档案内容");
        }
        return toView(existing);
    }

    private PresentedAuthorization requirePaymentEvidence(long accountId) {
        return paymentAuthorizationEvidence.findPaidAuthorization(accountId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "PREPAYMENT_AUTHORIZATION_EVIDENCE_MISSING",
                        "付款记录缺少付款前授权书证据"));
    }

    private ProfileRevisionView insertSnapshotMarkPendingAndAudit(
            GuestProfileSnapshot snapshot,
            String keyHmac,
            String payloadSha256,
            String requestId) {
        OffsetDateTime submittedAt = OffsetDateTime.now(clock);
        ProfileRevisionEntity revision = new ProfileRevisionEntity();
        revision.setGuestProfileId(snapshot.profileId());
        revision.setRevisionNumber(revisionMapper.selectNextRevisionNumber(snapshot.profileId()));
        revision.setGender(snapshot.gender());
        revision.setBirthDate(snapshot.birthDate());
        revision.setHeightCm(snapshot.heightCm());
        revision.setEducation(snapshot.education());
        revision.setOccupation(snapshot.occupation());
        revision.setIncomeRange(snapshot.incomeRange());
        revision.setCity(snapshot.city());
        revision.setWechatIdCiphertext(snapshot.wechatIdCiphertext());
        revision.setWechatIdHmac(snapshot.wechatIdHmac());
        revision.setDouyinIdCiphertext(snapshot.douyinIdCiphertext());
        revision.setDouyinIdHmac(snapshot.douyinIdHmac());
        revision.setDouyinNicknameCiphertext(snapshot.douyinNicknameCiphertext());
        revision.setDouyinProfileUrlCiphertext(snapshot.douyinProfileUrlCiphertext());
        revision.setStatus(RevisionStatus.PENDING);
        revision.setSubmittedByAccountId(snapshot.accountId());
        revision.setSubmittedAt(submittedAt);
        revision.setReviewDeadlineAt(submittedAt.plusHours(24));
        revision.setSubmissionKeyHmac(keyHmac);
        revision.setRequestPayloadSha256(payloadSha256);
        revision.setVersion(0L);
        revision.setCreatedAt(submittedAt);
        revisionMapper.insert(revision);

        for (GuestProfileSnapshot.FieldValue field : snapshot.dynamicFields()) {
            ProfileRevisionFieldValueEntity stored = new ProfileRevisionFieldValueEntity();
            stored.setProfileRevisionId(revision.getId());
            stored.setFieldCode(field.fieldCode());
            stored.setFieldLabel(field.fieldLabel());
            stored.setDataType(field.dataType());
            stored.setDisplayOption(field.displayOption());
            stored.setTextValue(field.textValue());
            stored.setIntegerValue(field.integerValue());
            stored.setDecimalValue(field.decimalValue());
            stored.setDateValue(field.dateValue());
            stored.setBooleanValue(field.booleanValue());
            stored.setOptionValue(field.optionValue());
            stored.setCreatedAt(submittedAt);
            revisionFieldMapper.insert(stored);
        }

        for (GuestProfileSnapshot.Photo photo : snapshot.photos()) {
            ProfileRevisionPhotoEntity stored = new ProfileRevisionPhotoEntity();
            stored.setProfileRevisionId(revision.getId());
            stored.setCategory(PhotoCategory.valueOf(photo.category()));
            stored.setObjectKey(photo.objectKey());
            stored.setSortOrder(photo.sortOrder());
            stored.setCreatedAt(submittedAt);
            revisionPhotoMapper.insert(stored);
        }

        profileApprovalPort.markPending(
                snapshot.profileId(), revision.getId(), snapshot.profileVersion());
        auditTrail.append(new AuditEvent(
                AuditEvent.ActorType.GUEST,
                snapshot.accountId(),
                "PROFILE_SUBMITTED",
                "PROFILE_REVISION",
                revision.getId(),
                requestId,
                "{\"revisionNumber\":" + revision.getRevisionNumber() + "}",
                submittedAt));
        return toView(revision);
    }

    private ProfileRevisionView toView(ProfileRevisionEntity revision) {
        List<ProfileRevisionView.FieldValue> fields = revisionFieldMapper.selectList(
                        Wrappers.<ProfileRevisionFieldValueEntity>lambdaQuery()
                                .eq(ProfileRevisionFieldValueEntity::getProfileRevisionId,
                                        revision.getId())
                                .orderByAsc(ProfileRevisionFieldValueEntity::getFieldCode))
                .stream()
                .map(field -> new ProfileRevisionView.FieldValue(
                        field.getFieldCode(),
                        field.getFieldLabel(),
                        field.getDataType(),
                        field.getDisplayOption(),
                        field.getTextValue(),
                        field.getIntegerValue(),
                        field.getDecimalValue(),
                        field.getDateValue(),
                        field.getBooleanValue(),
                        field.getOptionValue()))
                .toList();
        List<ProfileRevisionView.Photo> photos = revisionPhotoMapper.selectList(
                        Wrappers.<ProfileRevisionPhotoEntity>lambdaQuery()
                                .eq(ProfileRevisionPhotoEntity::getProfileRevisionId,
                                        revision.getId())
                                .orderByAsc(ProfileRevisionPhotoEntity::getCategory)
                                .orderByAsc(ProfileRevisionPhotoEntity::getSortOrder))
                .stream()
                .map(photo -> new ProfileRevisionView.Photo(
                        photo.getCategory().name(),
                        photo.getObjectKey(),
                        photo.getSortOrder(),
                        storageService.signDownloadUrl(
                                photo.getObjectKey(), Duration.ofMinutes(15))))
                .toList();
        return new ProfileRevisionView(
                revision.getId(),
                revision.getRevisionNumber(),
                revision.getStatus(),
                revision.getGender(),
                revision.getBirthDate(),
                revision.getHeightCm(),
                revision.getEducation(),
                revision.getOccupation(),
                revision.getIncomeRange(),
                revision.getCity(),
                revision.getSubmittedAt(),
                revision.getReviewDeadlineAt(),
                revision.getReviewedAt(),
                revision.getVersion(),
                fields,
                photos);
    }
}

@Component
final class CanonicalSnapshotHasher {

    private static final String WECHAT_ID_DOMAIN = "profile:wechat-id";
    private static final String DOUYIN_ID_DOMAIN = "profile:douyin-id";
    private static final String DOUYIN_NICKNAME_DOMAIN = "profile:douyin-nickname";
    private static final String DOUYIN_PROFILE_URL_DOMAIN = "profile:douyin-profile-url";

    private final SensitiveValueProtector protector;

    CanonicalSnapshotHasher(SensitiveValueProtector protector) {
        this.protector = java.util.Objects.requireNonNull(protector, "protector");
    }

    String sha256(GuestProfileSnapshot snapshot) {
        MessageDigest digest = sha256Digest();
        try (DataOutputStream output = new DataOutputStream(new DigestOutputStream(
                OutputStream.nullOutputStream(), digest))) {
            writeValue(output, "profile-submission-v1");
            writeEntry(output, "gender", snapshot.gender());
            writeEntry(output, "birth_date", snapshot.birthDate());
            writeEntry(output, "height_cm", snapshot.heightCm());
            writeEntry(output, "education", snapshot.education());
            writeEntry(output, "occupation", snapshot.occupation());
            writeEntry(output, "income_range", snapshot.incomeRange());
            writeEntry(output, "city", snapshot.city());
            writeEntry(output, "wechat_id", decrypt(
                    WECHAT_ID_DOMAIN, snapshot.wechatIdCiphertext()));
            writeEntry(output, "douyin_id", decrypt(
                    DOUYIN_ID_DOMAIN, snapshot.douyinIdCiphertext()));
            writeEntry(output, "douyin_nickname", decrypt(
                    DOUYIN_NICKNAME_DOMAIN, snapshot.douyinNicknameCiphertext()));
            writeEntry(output, "douyin_profile_url", decrypt(
                    DOUYIN_PROFILE_URL_DOMAIN, snapshot.douyinProfileUrlCiphertext()));

            List<GuestProfileSnapshot.FieldValue> fields = snapshot.dynamicFields().stream()
                    .sorted(Comparator.comparing(GuestProfileSnapshot.FieldValue::fieldCode))
                    .toList();
            output.writeInt(fields.size());
            for (GuestProfileSnapshot.FieldValue field : fields) {
                writeEntry(output, "field_code", field.fieldCode());
                writeEntry(output, "field_label", field.fieldLabel());
                writeEntry(output, "data_type", field.dataType());
                writeEntry(output, "display_option", field.displayOption());
                writeEntry(output, "text_value", field.textValue());
                writeEntry(output, "integer_value", field.integerValue());
                writeEntry(output, "decimal_value", field.decimalValue());
                writeEntry(output, "date_value", field.dateValue());
                writeEntry(output, "boolean_value", field.booleanValue());
                writeEntry(output, "option_value", field.optionValue());
            }

            List<GuestProfileSnapshot.Photo> photos = snapshot.photos().stream()
                    .sorted(Comparator.comparing(GuestProfileSnapshot.Photo::category)
                            .thenComparing(GuestProfileSnapshot.Photo::sortOrder))
                    .toList();
            output.writeInt(photos.size());
            for (GuestProfileSnapshot.Photo photo : photos) {
                writeEntry(output, "photo_category", photo.category());
                writeEntry(output, "photo_object_key", photo.objectKey());
                writeEntry(output, "photo_sort_order", photo.sortOrder());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("档案快照摘要计算失败", exception);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String decrypt(String domain, byte[] ciphertext) {
        return ciphertext == null ? null : protector.decrypt(domain, ciphertext);
    }

    private static void writeEntry(DataOutputStream output, String name, Object value)
            throws IOException {
        writeValue(output, name);
        if (value == null) {
            output.writeByte(0);
            return;
        }
        output.writeByte(1);
        byte[] bytes = canonicalBytes(value);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static void writeValue(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static byte[] canonicalBytes(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes.clone();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString().getBytes(StandardCharsets.UTF_8);
        }
        if (value instanceof LocalDate date) {
            return date.toString().getBytes(StandardCharsets.UTF_8);
        }
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
