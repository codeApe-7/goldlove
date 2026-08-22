import { describe, expect, it, vi } from 'vitest'
import { APP_NAME, pageTitle, setDocumentTitle } from './documentTitle'

describe('H5 标签页标题', () => {
  it('页面名后面接上品牌名', () => {
    expect(pageTitle('我的档案')).toBe('我的档案 · gold 智能档案库')
    expect(pageTitle('我的')).toBe('我的 · gold 智能档案库')
  })

  it('没有页面名时只用品牌名', () => {
    expect(pageTitle('')).toBe(APP_NAME)
  })

  it('写入 document.title', () => {
    setDocumentTitle('我的档案')
    expect(document.title).toBe('我的档案 · gold 智能档案库')
  })

  it('没有 document 的平台上不炸', () => {
    // 小程序 / App 端没有 document；标签页标题这件事只在 H5 上存在。
    const original = globalThis.document
    vi.stubGlobal('document', undefined)
    expect(() => setDocumentTitle('我的')).not.toThrow()
    vi.stubGlobal('document', original)
  })
})
