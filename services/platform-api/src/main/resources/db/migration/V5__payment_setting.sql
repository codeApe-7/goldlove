-- 支付金额改为后台可维护。
--
-- 原来 VIP 升级金额只有 VIP_UPGRADE_AMOUNT_MINOR 一个来源：改价要改服务器 .env
-- 再重建容器，运营自己动不了。现在把金额存一行到库里，管理后台直接改，
-- 改完对之后创建的订单立刻生效（已创建的订单保留它下单时的金额，不追溯）。
--
-- 语义是「覆盖」而不是「替换」：**表里没有行时仍然用配置值**。
-- 这条很重要——线上 VIP_UPGRADE_AMOUNT_MINOR=1（¥0.01），而配置默认值是 100（¥1.00），
-- 迁移里直接种一行就得二选一：种 100 会把线上价格翻 100 倍，种 1 又会把
-- 别的环境（本地 / 测试）的价格按线上写死。所以这里只建表，
-- 第一次在后台保存时才写入这一行。

CREATE TABLE payment_setting (
    -- 单行表：全局只有一份支付参数，不按渠道或商品分行。
    id SMALLINT NOT NULL DEFAULT 1 PRIMARY KEY,
    vip_upgrade_amount_minor BIGINT NOT NULL,
    updated_by_admin_id BIGINT REFERENCES admin_user (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_setting_singleton CHECK (id = 1),
    -- 上限 10000000 分（¥100000）是防呆：多打几个 0 不该真产生一笔十万元的订单。
    -- 下限 1 分——线上正是用 ¥0.01 验证支付链路的。
    CONSTRAINT ck_payment_setting_amount CHECK (vip_upgrade_amount_minor BETWEEN 1 AND 10000000)
);

COMMENT ON TABLE payment_setting IS
    '运营可改的支付参数，单行；无行时回落到 app.payment.online 配置';

-- 运行时账号只需要读写这一行，不给 DELETE：
-- 「改回配置值」应该是把金额改成那个数，而不是悄悄删掉一行让来源变回环境变量。
GRANT SELECT, INSERT, UPDATE ON payment_setting TO archive_app;
