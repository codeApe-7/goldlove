package com.love.archive.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.love.archive.testsupport.PostgresIntegrationTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * payment_setting 的库级不变量。金额上下限与「只有一行」都钉在库里，
 * 应用层的校验不是唯一防线——这张表的值直接决定用户会被收多少钱。
 */
@Transactional
class PaymentSettingPersistenceTest extends PostgresIntegrationTest {

    @Autowired private PaymentSettingMapper paymentSettingMapper;
    @Autowired private JdbcClient jdbcClient;

    @Test
    void upsertWritesThenOverwritesTheSingleRow() {
        OffsetDateTime now = OffsetDateTime.now();
        assertThat(paymentSettingMapper.upsert(12_800L, null, now)).isOne();
        assertThat(paymentSettingMapper.upsert(19_900L, null, now.plusMinutes(1))).isOne();

        PaymentSettingEntity stored = paymentSettingMapper.selectById(1);
        assertThat(stored.getVipUpgradeAmountMinor()).isEqualTo(19_900L);
        assertThat(rowCount()).isOne();
    }

    @Test
    void rejectsASecondRow() {
        assertThatThrownBy(() -> jdbcClient.sql("""
                        INSERT INTO payment_setting (id, vip_upgrade_amount_minor)
                        VALUES (2, 100)
                        """).update())
                .hasStackTraceContaining("ck_payment_setting_singleton");
    }

    @Test
    void rejectsAZeroAmount() {
        assertThatThrownBy(() -> insertAmount(0L))
                .hasStackTraceContaining("ck_payment_setting_amount");
    }

    @Test
    void rejectsAnAmountAboveTheCeiling() {
        assertThatThrownBy(() -> insertAmount(10_000_001L))
                .hasStackTraceContaining("ck_payment_setting_amount");
    }

    @Test
    void runtimeRoleCanReadAndWriteButNotDelete() {
        assertThat(jdbcClient.sql("SELECT current_user").query(String.class).single())
                .isEqualTo("archive_app");
        assertThat(hasPrivilege("SELECT")).isTrue();
        assertThat(hasPrivilege("INSERT")).isTrue();
        assertThat(hasPrivilege("UPDATE")).isTrue();
        // 「改回配置值」是把金额改成那个数，不是删掉这一行让来源悄悄变回环境变量。
        assertThat(hasPrivilege("DELETE")).isFalse();
    }

    private void insertAmount(long amountMinor) {
        jdbcClient.sql("INSERT INTO payment_setting (id, vip_upgrade_amount_minor) VALUES (1, :amount)")
                .param("amount", amountMinor)
                .update();
    }

    private boolean hasPrivilege(String privilege) {
        return jdbcClient
                .sql("SELECT has_table_privilege(current_user, 'payment_setting', :privilege)")
                .param("privilege", privilege)
                .query(Boolean.class)
                .single();
    }

    private long rowCount() {
        return jdbcClient.sql("SELECT count(*) FROM payment_setting").query(Long.class).single();
    }
}
