-- 线上支付渠道泛化：易支付（XPay V2，指定支付宝）接入，微信渠道保留。
-- wechat_payment_order 承载所有线上渠道订单：新增 channel 区分渠道，
-- openid 字段对无需授权前置的渠道（易支付）可空。

ALTER TABLE wechat_payment_order
    ADD COLUMN channel VARCHAR(24) NOT NULL DEFAULT 'WECHAT_JSAPI';

ALTER TABLE wechat_payment_order
    ALTER COLUMN openid_ciphertext DROP NOT NULL,
    ALTER COLUMN openid_hmac DROP NOT NULL;

-- 注册令牌的 openid 同样对无授权渠道可空。
ALTER TABLE registration_token
    ALTER COLUMN openid_ciphertext DROP NOT NULL,
    ALTER COLUMN openid_hmac DROP NOT NULL;

ALTER TABLE wechat_payment_order
    ADD CONSTRAINT ck_wechat_payment_order_channel
        CHECK (channel IN ('WECHAT_JSAPI', 'XPAY_ALIPAY')),
    ADD CONSTRAINT ck_wechat_payment_order_payer_shape CHECK (
        (channel = 'XPAY_ALIPAY' AND openid_ciphertext IS NULL AND openid_hmac IS NULL)
        OR (channel = 'WECHAT_JSAPI' AND openid_ciphertext IS NOT NULL AND openid_hmac IS NOT NULL)
    );

-- payment_record.payment_channel 增加易支付支付宝；线上渠道 shape 约束统一覆盖两个线上渠道。
ALTER TABLE payment_record DROP CONSTRAINT ck_payment_record_channel;
ALTER TABLE payment_record
    ADD CONSTRAINT ck_payment_record_channel
        CHECK (payment_channel IN ('MANUAL', 'WECHAT_JSAPI', 'XPAY_ALIPAY'));

ALTER TABLE payment_record DROP CONSTRAINT ck_payment_record_online_shape;
ALTER TABLE payment_record
    ADD CONSTRAINT ck_payment_record_online_shape CHECK (
        payment_channel NOT IN ('WECHAT_JSAPI', 'XPAY_ALIPAY')
        OR (operator_admin_id IS NULL
            AND out_trade_no IS NOT NULL
            AND payment_reference = out_trade_no
            AND paid_amount_minor IS NOT NULL
            AND transaction_id_ciphertext IS NOT NULL
            AND transaction_id_hmac IS NOT NULL)
    );

-- user_account.registration_channel 增加线上建档（易支付）。
ALTER TABLE user_account DROP CONSTRAINT ck_user_account_registration_channel;
ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_registration_channel
        CHECK (registration_channel IN ('ADMIN_MANUAL', 'WECHAT_ONLINE', 'ONLINE'));
