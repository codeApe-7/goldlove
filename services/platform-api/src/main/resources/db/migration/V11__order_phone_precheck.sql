-- 线上下单前置手机号预检：订单上记录手机号的不可逆比对令牌。
-- 用途：下单前拦截「该手机号已有账号」、复用已支付未注册订单、注册时校验手机号一致。
-- 该表不存手机号明文或密文，只存 HMAC，payment 模块因此永远看不到手机号本身。

ALTER TABLE wechat_payment_order
    ADD COLUMN phone_token VARCHAR(64);

COMMENT ON COLUMN wechat_payment_order.phone_token IS
    '规范化手机号的 HMAC-SHA256（domain=registration:phone）；此表不存手机号明文或密文';

-- 按手机号找回该用户最近的订单（复用已支付未注册订单、丢失 out_trade_no 后的恢复）。
CREATE INDEX ix_wechat_payment_order_phone_token
    ON wechat_payment_order (phone_token, created_at DESC);

-- 不加 NOT NULL：V11 之前建立的订单没有令牌，应用侧 insertCreated 保证新订单必填。
-- 无需新增授权：V9 已按表授予 archive_app SELECT/INSERT/UPDATE，表级授权自动覆盖新列。
