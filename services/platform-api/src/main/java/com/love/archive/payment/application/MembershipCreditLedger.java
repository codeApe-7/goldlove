package com.love.archive.payment.application;

/**
 * 会员额度记账出站接口：把「某笔付款是否已计入 SVIP 累计额度」的幂等锚点留在付款记录上，
 * 由 identity 侧的会员服务驱动，避免 payment 反向依赖 identity。
 */
public interface MembershipCreditLedger {

    /**
     * 把付款记录标记为「已计入会员额度」。同一笔付款重复调用只有第一次返回 true。
     *
     * @return 本次调用是否真正完成了记账
     */
    boolean claimCredit(long paymentRecordId, long creditMinor);
}
