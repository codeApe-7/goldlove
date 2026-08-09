package com.love.archive.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.guest.persistence.GuestProfileEntity;
import com.love.archive.identity.persistence.ActivationCredentialEntity;
import com.love.archive.identity.persistence.ExternalIdentityEntity;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.review.persistence.ProfileRevisionEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LombokEntitySafetyTest {

    @Test
    void userAccountCiphertextIsDefensivelyCopiedOnWriteAndRead() {
        UserAccountEntity account = new UserAccountEntity();
        byte[] supplied = {1, 2, 3};

        account.setPhoneCiphertext(supplied);
        supplied[0] = 9;
        byte[] returned = account.getPhoneCiphertext();
        returned[1] = 9;

        assertThat(account.getPhoneCiphertext()).containsExactly(1, 2, 3);
    }

    @Test
    void externalIdentityCiphertextsAreDefensivelyCopiedOnWriteAndRead() {
        ExternalIdentityEntity identity = new ExternalIdentityEntity();
        byte[] subject = {1, 2, 3};
        byte[] unionId = {4, 5, 6};

        identity.setSubjectCiphertext(subject);
        identity.setUnionIdCiphertext(unionId);
        subject[0] = 9;
        unionId[0] = 9;
        identity.getSubjectCiphertext()[1] = 9;
        identity.getUnionIdCiphertext()[1] = 9;

        assertThat(identity.getSubjectCiphertext()).containsExactly(1, 2, 3);
        assertThat(identity.getUnionIdCiphertext()).containsExactly(4, 5, 6);
    }

    @Test
    void entityStringRepresentationsDoNotExposeStoredSecrets() {
        AdminUserEntity admin = new AdminUserEntity();
        admin.setPasswordHash("admin-password-hash-secret");
        UserAccountEntity account = new UserAccountEntity();
        account.setPasswordHash("guest-password-hash-secret");
        ActivationCredentialEntity credential = new ActivationCredentialEntity();
        credential.setCredentialHash("activation-hash-secret");
        ExternalIdentityEntity identity = new ExternalIdentityEntity();
        identity.setSubjectHmac("wechat-subject-hmac-secret");

        assertThat(admin.toString()).doesNotContain("admin-password-hash-secret");
        assertThat(account.toString()).doesNotContain("guest-password-hash-secret");
        assertThat(credential.toString()).doesNotContain("activation-hash-secret");
        assertThat(identity.toString()).doesNotContain("wechat-subject-hmac-secret");
    }

    @Test
    void profileAndRevisionCiphertextsAreDefensivelyCopied() {
        GuestProfileEntity profile = new GuestProfileEntity();
        byte[] wechat = {1, 2, 3};
        byte[] douyin = {4, 5, 6};
        byte[] nickname = {7, 8, 9};
        byte[] profileUrl = {10, 11, 12};
        profile.setWechatIdCiphertext(wechat);
        profile.setDouyinIdCiphertext(douyin);
        profile.setDouyinNicknameCiphertext(nickname);
        profile.setDouyinProfileUrlCiphertext(profileUrl);
        wechat[0] = 99;
        douyin[0] = 99;
        nickname[0] = 99;
        profileUrl[0] = 99;
        profile.getWechatIdCiphertext()[1] = 99;
        profile.getDouyinIdCiphertext()[1] = 99;
        profile.getDouyinNicknameCiphertext()[1] = 99;
        profile.getDouyinProfileUrlCiphertext()[1] = 99;

        assertThat(profile.getWechatIdCiphertext()).containsExactly(1, 2, 3);
        assertThat(profile.getDouyinIdCiphertext()).containsExactly(4, 5, 6);
        assertThat(profile.getDouyinNicknameCiphertext()).containsExactly(7, 8, 9);
        assertThat(profile.getDouyinProfileUrlCiphertext()).containsExactly(10, 11, 12);

        ProfileRevisionEntity revision = new ProfileRevisionEntity();
        byte[] revisionWechat = {21, 22};
        byte[] revisionDouyin = {23, 24};
        byte[] revisionNickname = {25, 26};
        byte[] revisionProfileUrl = {27, 28};
        revision.setWechatIdCiphertext(revisionWechat);
        revision.setDouyinIdCiphertext(revisionDouyin);
        revision.setDouyinNicknameCiphertext(revisionNickname);
        revision.setDouyinProfileUrlCiphertext(revisionProfileUrl);
        revisionWechat[0] = 99;
        revisionDouyin[0] = 99;
        revisionNickname[0] = 99;
        revisionProfileUrl[0] = 99;
        revision.getWechatIdCiphertext()[1] = 99;
        revision.getDouyinIdCiphertext()[1] = 99;
        revision.getDouyinNicknameCiphertext()[1] = 99;
        revision.getDouyinProfileUrlCiphertext()[1] = 99;

        assertThat(revision.getWechatIdCiphertext()).containsExactly(21, 22);
        assertThat(revision.getDouyinIdCiphertext()).containsExactly(23, 24);
        assertThat(revision.getDouyinNicknameCiphertext()).containsExactly(25, 26);
        assertThat(revision.getDouyinProfileUrlCiphertext()).containsExactly(27, 28);
    }

    @Test
    void profileReviewAndConsentEntitiesDoNotUseDataOrToString() throws IOException {
        String entities = readTree(Path.of("src/main/java/com/love/archive/guest/persistence"))
                + readTree(Path.of("src/main/java/com/love/archive/review/persistence"))
                + readTree(Path.of("src/main/java/com/love/archive/consent/persistence"));

        assertThat(entities)
                .doesNotContain("lombok.Data")
                .doesNotContain("@Data")
                .doesNotContain("lombok.ToString")
                .doesNotContain("@ToString");
    }

    private static String readTree(Path root) throws IOException {
        StringBuilder content = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                content.append(Files.readString(file)).append('\n');
            }
        }
        return content.toString();
    }
}
