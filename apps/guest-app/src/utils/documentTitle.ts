/**
 * H5 浏览器标签页标题。
 *
 * uni-app 在 H5 上会把 `navigationBarTitleText` 同时用作原生导航栏文字和
 * `document.title`。档案页与「我的」页有原生导航栏，那里必须是短标题
 * （390px 宽放不下品牌名），但浏览器标签、书签和分享出去的链接应该带上品牌名——
 * 管理后台的标签页就是「gold 智能档案库 · 管理后台」，两端不该只有一边有。
 *
 * 所以这里在页面 onShow 里单独覆盖 document.title，不动导航栏。
 * 非 H5 平台没有 document，直接跳过。
 */
export const APP_NAME = 'gold 智能档案库'

const SEPARATOR = ' · '

export function pageTitle(pageName: string): string {
  return pageName ? `${pageName}${SEPARATOR}${APP_NAME}` : APP_NAME
}

export function setDocumentTitle(pageName: string): void {
  if (typeof document === 'undefined') {
    return
  }
  document.title = pageTitle(pageName)
}
