import { describe, expect, it } from 'vitest'
import { renderCourseMarkdown, sanitizeCourseHtml } from './courseMarkdown'

describe('renderCourseMarkdown', () => {
  it('空正文渲染成空串，页面据此走「无正文」分支', () => {
    expect(renderCourseMarkdown(null)).toBe('')
    expect(renderCourseMarkdown(undefined)).toBe('')
    expect(renderCourseMarkdown('')).toBe('')
    expect(renderCourseMarkdown('   \n\t  ')).toBe('')
  })

  it('标题、粗体、列表、引用、代码块都能出常规标签', () => {
    const html = renderCourseMarkdown(
      '# 第一课\n\n**要点**在于观察。\n\n- 甲\n- 乙\n\n> 引用\n\n```\ncode\n```\n',
    )

    expect(html).toContain('<h1>第一课</h1>')
    expect(html).toContain('<strong>要点</strong>')
    expect(html).toContain('<ul>')
    expect(html).toContain('<li>甲</li>')
    expect(html).toContain('<blockquote>')
    expect(html).toContain('<pre>')
    expect(html).toContain('<code>')
  })

  it('图片保留 src 与 alt（封面之外的插图靠它）', () => {
    const html = renderCourseMarkdown('![示意图](https://cdn.example.com/a.png)')

    expect(html).toContain('<img')
    expect(html).toContain('src="https://cdn.example.com/a.png"')
    expect(html).toContain('alt="示意图"')
  })

  it('链接在新标签打开并带 noopener，避免把 H5 应用整页跳走', () => {
    const html = renderCourseMarkdown('[延伸阅读](https://example.com/x)')

    expect(html).toContain('href="https://example.com/x"')
    expect(html).toContain('target="_blank"')
    expect(html).toContain('rel="noopener noreferrer"')
  })

  it('正文里的裸 HTML 不会变成真标签', () => {
    // markdown-it 关掉了 html 选项：后台录入的正文里出现 HTML 一律按纯文本转义。
    const html = renderCourseMarkdown('正文\n\n<script>alert(1)</script>\n')

    expect(html).not.toContain('<script')
    expect(html).toContain('&lt;script&gt;')
  })

  it('裸 HTML 里的事件属性同样进不来', () => {
    const html = renderCourseMarkdown('<img src=x onerror="alert(1)">')

    // html:false 把整段转义成文本：没有 img 元素，也就没有能触发的 onerror。
    expect(html).not.toContain('<img')
    expect(html).toContain('&lt;img')
  })

  it('javascript: 链接不会变成可点的地址', () => {
    // markdown-it 的 validateLink 认不出这个协议，整行退化成纯文本，连 <a> 都不生成。
    // DOMPurify 那一层的同类断言见下面的 sanitizeCourseHtml 用例。
    const html = renderCourseMarkdown('[点我](javascript:alert(1))')

    expect(html).not.toContain('<a ')
    expect(html).not.toContain('href')
  })

  it('中文、emoji、特殊字符原样保留', () => {
    const html = renderCourseMarkdown('沟通 🌱 与 5 < 7 & "引号" 的 O\'Brien 情况')

    expect(html).toContain('🌱')
    expect(html).toContain('5 &lt; 7')
    expect(html).toContain('&amp;')
    expect(html).toContain('O\'Brien')
  })

  it('SQL 味道的字符只是普通文本，不做任何特殊处理', () => {
    const html = renderCourseMarkdown("举例：DROP TABLE course; -- 注释")

    expect(html).toContain('DROP TABLE course; -- 注释')
  })

  it('长正文（5000 段）也能在一秒内渲染完', () => {
    const source = Array.from({ length: 5000 }, (_, index) => `第 ${index} 段正文。`).join('\n\n')
    const started = Date.now()

    const html = renderCourseMarkdown(source)

    expect(html).toContain('第 4999 段正文。')
    expect(Date.now() - started).toBeLessThan(1000)
  })
})

describe('sanitizeCourseHtml', () => {
  it('保留正文标签，剔掉脚本', () => {
    const html = sanitizeCourseHtml('<p>正文</p><script>alert(1)</script>')

    expect(html).toContain('<p>正文</p>')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('alert(1)')
  })

  it('剔掉事件属性但留下元素本身', () => {
    const html = sanitizeCourseHtml('<img src="https://cdn.example.com/a.png" onerror="alert(1)">')

    expect(html).toContain('src="https://cdn.example.com/a.png"')
    expect(html).not.toContain('onerror')
  })

  it('剔掉 javascript: 协议的链接地址', () => {
    const html = sanitizeCourseHtml('<a href="javascript:alert(1)">点我</a>')

    expect(html).not.toContain('javascript:')
    expect(html).toContain('点我')
  })

  it('剔掉 iframe、form、style 这类不该出现在课程正文里的元素', () => {
    const html = sanitizeCourseHtml(
      '<iframe src="https://evil.example"></iframe>'
      + '<form action="/x"><input name="a"></form>'
      + '<style>body{display:none}</style>'
      + '<p>正文</p>',
    )

    expect(html).not.toContain('<iframe')
    expect(html).not.toContain('<form')
    expect(html).not.toContain('<input')
    expect(html).not.toContain('<style')
    expect(html).toContain('<p>正文</p>')
  })

  it('剔掉内联 style，避免正文盖住页面自己的元素', () => {
    const html = sanitizeCourseHtml('<p style="position:fixed;top:0;left:0;">遮罩</p>')

    expect(html).not.toContain('position')
    expect(html).toContain('遮罩')
  })

  it('空输入与非字符串输入不抛异常', () => {
    expect(sanitizeCourseHtml('')).toBe('')
    expect(sanitizeCourseHtml(null as unknown as string)).toBe('')
    expect(sanitizeCourseHtml(undefined as unknown as string)).toBe('')
  })
})
