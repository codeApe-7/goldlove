import { describe, expect, it } from 'vitest'
import iconSource from './AppIcon.vue?raw'
import minePageSource from '../pages/mine/index.vue?raw'

describe('AppIcon prototype fidelity', () => {
  it('uses SVG paths instead of font glyph placeholders', () => {
    expect(iconSource).toContain('<view class="app-icon"')
    expect(iconSource).toContain('<svg')
    expect(iconSource).toContain('stroke="currentColor"')
    expect(iconSource).not.toContain("document: '▤'")
    expect(iconSource).not.toContain("shield: '◇'")
    expect(iconSource).not.toContain("headset: '◡'")
  })

  it('assigns distinct prototype icons to the mine page rows', () => {
    expect(minePageSource).toContain('name="authorization"')
    expect(minePageSource).toContain('name="shield"')
    expect(minePageSource).toContain('name="document"')
    expect(minePageSource).toContain('name="account"')
    expect(minePageSource).toContain('name="help"')
    expect(minePageSource).toContain('name="headset"')
  })
})
