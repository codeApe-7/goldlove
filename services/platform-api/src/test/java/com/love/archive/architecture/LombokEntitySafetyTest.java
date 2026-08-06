package com.love.archive.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.identity.persistence.ActivationCredentialEntity;
import com.love.archive.identity.persistence.ExternalIdentityEntity;
import com.love.archive.identity.persistence.UserAccountEntity;
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
}
