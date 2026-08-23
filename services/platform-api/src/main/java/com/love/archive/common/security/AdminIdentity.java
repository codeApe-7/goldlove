package com.love.archive.common.security;

/**
 * 当前登录管理员的账号 id。
 *
 * <p>与 {@link GuestAccountIdentity} 同一个理由存在：sa-token 的 {@code StpLogic}
 * 装在 identity 模块的 {@code AuthLogics} 里，而 Modulith 只允许其他模块依赖
 * {@code common::security}。需要知道「是哪个管理员在操作」（写审计）的模块
 * 依赖这个接口，而不是去依赖 identity。</p>
 */
public interface AdminIdentity {

    long currentAdminId();
}
