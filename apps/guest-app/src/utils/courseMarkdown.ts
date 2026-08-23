/**
 * 课程正文（markdown）渲染与净化。
 *
 * ## 为什么用 `v-html` 而不是 `rich-text`
 *
 * 两条都能在 H5 上出效果，取舍如下（结论：`v-html`）：
 *
 * - `rich-text` 会按自己的白名单二次过滤。查 `@dcloudio/uni-h5` 的实现：`TAGS` 表里
 *   `a: ""`，而 `normalizeAttrs()` 只保留 `class` / `style` 与该标签白名单内的属性 ——
 *   也就是说**链接的 `href` 会被直接丢掉**，课程正文里的延伸阅读会变成一段死文字。
 *   `img` 只留 `alt/src/height/width`，其余标签一律无属性。
 * - 正文净化本来就得我们自己做（后台录入的富文本不可信）。用 `v-html` 时页面显示的
 *   就是 DOMPurify 批准的那份 HTML，白名单只有一处、可审计；用 `rich-text` 等于叠两套
 *   互不知情的白名单，最终显示什么很难预测。
 * - 样式两者都能用 scoped CSS 命中（`rich-text` 会把 scopeId 透给子节点），这一项是平手。
 * - 代价：**`v-html` 只在 H5 有效**，小程序端不支持。本项目首期只发 H5，且
 *   `adapters/session.ts`、`adapters/payment.ts`、`api/request.ts` 已经是 H5-only 实现
 *   （`sessionStorage` / `window.location`）。真要上小程序时，这里换成 `rich-text` +
 *   `parseHtml` 即可，净化层不动。
 */
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

/**
 * `html: false` —— 正文里出现的裸 HTML 一律转义成文本，不给它变成标签的机会。
 * 这是第一层；DOMPurify 是第二层，两层都在，删掉任何一层都算回归。
 * `breaks: true` —— 后台是在文本框里录入的，单个换行按软换行处理更符合作者预期。
 * `typographer: false` —— 中文引号、破折号不要被替换成西文样式。
 */
const renderer = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: false,
})

/** 课程正文只可能出现这些标签（markdown 的产出集合），多余的一律不放行。 */
const ALLOWED_TAGS = [
  'p', 'br', 'hr', 'blockquote', 'pre', 'code',
  'strong', 'em', 'del', 's', 'sub', 'sup', 'span',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'ul', 'ol', 'li',
  'a', 'img',
  'table', 'thead', 'tbody', 'tr', 'th', 'td',
]

/**
 * 不放 `style`：正文一旦能写内联样式，就能 `position: fixed` 盖住页面自己的按钮。
 * 不放 `class`：正文没有理由去命中页面的样式类。
 */
const ALLOWED_ATTR = ['href', 'title', 'src', 'alt', 'colspan', 'rowspan']

/**
 * 正文里的外链在新标签打开：H5 是单页应用，同标签跳走会把整个应用卸掉。
 * `rel` 同时挡住反向 tabnabbing。钩子在属性过滤之后执行，所以这两个属性
 * 不需要（也不应该）出现在 ALLOWED_ATTR 里。
 */
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A' && node.hasAttribute('href')) {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

/** 净化。第二层防线，也是唯一决定「最终能显示什么」的地方。 */
export function sanitizeCourseHtml(html: string): string {
  if (typeof html !== 'string' || html === '') {
    return ''
  }
  // 没有 DOM 的环境（例如 SSR 预渲染）里 DOMPurify 是空转的，会原样返回输入。
  // 那种情况下宁可什么都不显示，也不能把未净化的 HTML 交给 v-html。
  if (!DOMPurify.isSupported) {
    return ''
  }
  return DOMPurify.sanitize(html, { ALLOWED_TAGS, ALLOWED_ATTR })
}

/** markdown → HTML → 净化。页面拿到的字符串可以直接交给 `v-html`。 */
export function renderCourseMarkdown(markdown: string | null | undefined): string {
  if (typeof markdown !== 'string' || markdown.trim() === '') {
    return ''
  }
  return sanitizeCourseHtml(renderer.render(markdown))
}
