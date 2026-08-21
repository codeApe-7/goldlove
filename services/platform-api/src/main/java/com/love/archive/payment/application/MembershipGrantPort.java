package com.love.archive.payment.application;

/**
 * 会员授予出站接口：支付结算成功后由 payment 调用，identity 侧实现。
 *
 * <p>方向是刻意的——{@code identity} 已经依赖 {@code payment::application}，
 * 反向依赖会构成模块环。和 {@link MembershipCreditLedger} 一样，
 * payment 只声明需要什么，不知道会员体系怎么算。</p>
 */
public interface MembershipGrantPort {

    /**
     * 把一笔已结算的付款计入会员额度并按需升级等级。同一笔付款重复调用只生效一次。
     *
     * @param accountId       付款账号
     * @param paymentRecordId 付款记录主键，充当幂等锚点
     * @param creditMinor     计入的额度（分）
     */
    void grantPaidMembership(long accountId, long paymentRecordId, long creditMinor);
}
