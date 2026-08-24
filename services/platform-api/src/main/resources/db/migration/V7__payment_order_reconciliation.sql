-- 订单可对账、可过期、可被本人列出。
--
-- 背景是线上一笔订单查不出状态、用户也找不回它。排查后发现三件事：
--
-- 1. 平台侧的订单号我们拿到了却没存。下单的 302 响应里 Location 就是它
--    （`/pay/20260823225910918724`），查单响应里 `trade_no` 也带着它，而且**未支付也带**。
--    但 `transaction_id` 只在结算时才写，于是一笔没付成的订单在库里没有任何
--    网关侧的句柄——去渠道后台对账时无从下手。
--
-- 2. `status` 的 CHECK 允许 'CLOSED'，但全仓库没有一处写过它，也没有过期时间。
--    网关那边订单早就失效了（查单返回「没有找到订单信息」），我们这边还是 CREATED，
--    前端因此分不清「还能继续付」和「已经死了，得重新下单」。
--
-- 3. 用户看不到自己的订单——那是接口缺失，不是表缺列：
--    `ix_payment_order_account (user_account_id, created_at DESC)` 从 V1 就在了。
--
-- 这次只加列与索引，不动既有列的语义，也不需要新的 GRANT：
-- V1 末尾已经给了 archive_app 对 payment_order 的 SELECT / INSERT / UPDATE（没有 DELETE）。

ALTER TABLE payment_order
    ADD COLUMN channel_trade_no VARCHAR(64),
    ADD COLUMN expires_at TIMESTAMPTZ;

COMMENT ON COLUMN payment_order.channel_trade_no IS
    '渠道侧订单号（易支付 trade_no），下单成功即可记录，与是否支付无关';
COMMENT ON COLUMN payment_order.expires_at IS
    '订单可支付截止时间；到点仍未支付则转 CLOSED。存量行为 NULL，视为不过期';

-- 与 transaction_id 分成两列而不是复用一列：
--   channel_trade_no —— 渠道侧「这笔单子」的编号，下单就有
--   transaction_id   —— 「这笔钱」的流水号，结算才有
-- 易支付这两个值恰好相同，但语义不同（别的渠道也不一定相同）。
-- 更要紧的是 ck_payment_order_paid_shape 依赖「transaction_id 非空 ⇒ 已付」这个直觉，
-- 提前把渠道单号塞进去会把它变成一句谎话。
CREATE UNIQUE INDEX uq_payment_order_channel_trade_no
    ON payment_order (channel_trade_no) WHERE channel_trade_no IS NOT NULL;

-- 「我的订单」列表按账号取最近若干条，(user_account_id, created_at DESC) 已够用；
-- 这条补的是「找出该关掉的过期订单」——只扫未终态的行。
CREATE INDEX ix_payment_order_expiry
    ON payment_order (expires_at) WHERE status = 'CREATED';

-- 存量的 CREATED 订单补一个截止时间，否则它们会永远显示「待支付」——
-- 这正是当初报障的样子：上午下的单，晚上进来还是待支付，而渠道那边早就把它清掉了
-- （查单返回「没有找到订单信息」）。
--
-- 5 分钟与 app.payment.online.order-expiry-minutes 的默认值一致，也就是易支付收银台自己的超时。
-- 这里写死是有意的：迁移是一次性的事实修正，不该随日后配置改动而变。而且真有哪一笔在关单后
-- 才付成，回调与补偿查单依然会把 CLOSED 结算掉，不会丢单。
UPDATE payment_order
   SET expires_at = created_at + INTERVAL '5 minutes'
 WHERE status = 'CREATED' AND expires_at IS NULL;
