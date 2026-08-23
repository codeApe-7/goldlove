package com.love.archive.payment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 后台维护的支付参数，全表只有 id = 1 这一行。
 * 行不存在时表示「后台没设过」，金额回落到 app.payment.online 配置。
 */
@Getter
@Setter
@TableName("payment_setting")
public class PaymentSettingEntity {

    @TableId(type = IdType.INPUT)
    private Integer id;
    private Long vipUpgradeAmountMinor;
    private Long updatedByAdminId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
