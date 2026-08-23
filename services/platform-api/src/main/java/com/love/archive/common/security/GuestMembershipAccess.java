package com.love.archive.common.security;

/**
 * 访客是否已是付费会员。
 *
 * <p>与 {@link AdminIdentity} 同一个理由存在：会员等级枚举 {@code MembershipTier} 在
 * {@code identity.domain}，而那个包**没有** {@code @NamedInterface}，跨模块引用它会被
 * {@code ModularityTest} 判成对未导出包的非法依赖。所以这里只暴露一个 boolean，
 * 不把等级本身漏出去——需要「这个账号能不能看付费内容」的模块依赖这个接口，
 * 而不是去依赖 identity。</p>
 */
public interface GuestMembershipAccess {

    /**
     * 账号是否已升级到付费等级（VIP 及以上）。
     *
     * <p>账号不存在时返回 {@code false} 而不是抛异常：这个端口只回答「能不能看」，
     * 「账号存不存在」由调用方各自的鉴权链路负责。</p>
     */
    boolean isPaidMember(long accountId);
}
