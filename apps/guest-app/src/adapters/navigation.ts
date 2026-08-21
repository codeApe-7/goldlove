/**
 * 页面返回。
 *
 * `uni.navigateBack()` 只有当前页是被 `navigateTo` 压进页面栈时才有上一级可退。
 * 会员页同时是易支付 `return_url` 的落点（`#/pages/vip/index`），渠道整页跳转回来后
 * 是一次全新的文档加载，页面栈里只剩它自己 —— 此时 navigateBack 以
 * `cannot navigate back at first page` 失败，而默认没有 fail 回调，
 * 用户看到的就是「点返回没反应」。直接打开、刷新、收藏该地址同理。
 *
 * 所以返回必须带一个确定的兜底落点，而不是指望浏览器历史 —— H5 上退回去是收银台。
 */

/** `pages.json` 的 `tabBar.list`：跳这些页面只能用 switchTab，navigateTo / redirectTo 会失败。 */
const TAB_BAR_PAGES = new Set(['/pages/profile/index', '/pages/mine/index'])

/** 页面栈里是否还有上一级。取不到运行时（如 jsdom 测试环境）就当作没有。 */
function hasPreviousPage(): boolean {
  if (typeof getCurrentPages !== 'function') return false
  try {
    return getCurrentPages().length > 1
  } catch {
    return false
  }
}

/** 直接落到某个页面，按 tabBar / 普通页自动选 API。 */
export function goToPage(url: string): void {
  if (TAB_BAR_PAGES.has(url)) {
    uni.switchTab({ url })
    return
  }
  uni.redirectTo({ url })
}

/** 有上一级就退回去；页面栈是断的（或退失败）就落到 `fallbackUrl`。 */
export function goBackOr(fallbackUrl: string): void {
  if (!hasPreviousPage()) {
    goToPage(fallbackUrl)
    return
  }
  uni.navigateBack({ fail: () => goToPage(fallbackUrl) })
}
