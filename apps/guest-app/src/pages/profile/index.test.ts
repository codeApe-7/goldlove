import { describe, expect, it } from 'vitest'
import profilePageSource from './index.vue?raw'
import sectionCardSource from '../../components/SectionCard.vue?raw'
import selectSource from '../../components/AppSelect.vue?raw'
import wheelSource from '../../components/AppWheelPicker.vue?raw'

/**
 * 这些断言锁住三个真实踩过的坑，都是删掉一行就复现的回归。
 * 组件行为无法在 jsdom 里跑 uni-app 运行时，只能按仓库既有的 ?raw 保真断言风格守。
 */
describe('档案页不会清空未保存的输入', () => {
  it('onShow 只在首次填充表单', () => {
    // 原来每次 onShow 都 Object.assign(values, draftToProfileValues(...))：
    // H5 选图打开系统文件框再回来、或切到「我的」再切回来，都会重新触发 onShow，
    // 用服务端草稿覆盖表单会把还没保存的输入整片清空（缺失字段返回 ''）。
    expect(profilePageSource).toContain('const hydrated = ref(false)')
    expect(profilePageSource).toMatch(
      /if \(!hydrated\.value\)\s*\{[\s\S]*?Object\.assign\(values, draftToProfileValues/,
    )
    expect(profilePageSource).toContain('hydrated.value = true')
  })

  it('Object.assign 只出现在这一处守卫里', () => {
    const assigns = profilePageSource.match(/Object\.assign\(values/g) ?? []
    expect(assigns).toHaveLength(1)
  })
})

describe('内联下拉面板不会被卡片裁掉', () => {
  it('SectionCard 的裁切可以关掉', () => {
    // .section-card 原本无条件 overflow: hidden，绝对定位的下拉面板会被裁到
    // 只剩最上面几个选项——字段越靠卡片底部，能看到的选项越少。
    expect(sectionCardSource).toContain('clip?: boolean')
    expect(sectionCardSource).toContain('.section-card.clip { overflow: hidden; }')
    expect(sectionCardSource).not.toMatch(/\.section-card \{[^}]*overflow: hidden/)
  })

  it('档案页含下拉的字段卡片关掉了裁切', () => {
    expect(profilePageSource).toContain(':clip="false"')
  })

  it('面板高度够放下学历 6 项与行业 7 项', () => {
    const maxHeight = selectSource.match(/\.options \{[\s\S]*?max-height: (\d+)rpx/)
    expect(maxHeight).not.toBeNull()
    expect(Number(maxHeight?.[1])).toBeGreaterThanOrEqual(7 * 88)
  })
})

describe('滚轮的居中项与未选中项颜色分开', () => {
  it('按下标标记居中项，而不是全列同色', () => {
    // picker-view 无法用 CSS 命中「居中那一项」，必须拿 value 的下标自己标。
    expect(wheelSource).toContain("'is-active': itemIndex === value[columnIndex]")
    expect(wheelSource).toMatch(/\.wheel-item \{[\s\S]*?color: \$ds-placeholder/)
    expect(wheelSource).toMatch(/\.wheel-item\.is-active \{[\s\S]*?color: \$ds-ink/)
  })

  it('遮罩只盖上下两条，不能用 background 简写盖住居中项', () => {
    // uni-app 给遮罩加了内联 background-size: 100% <遮罩高度>px。
    // 用 background 简写会把 position 重置为 0 0、repeat 重置为 repeat，
    // 两条渐变平铺下来正好盖住居中那一行，把品牌黑洗成灰——这就是原来的 bug。
    const mask = wheelSource.match(/\.wheel-mask \{[\s\S]*?\n\}/)?.[0] ?? ''
    expect(mask).not.toBe('')
    expect(mask).toContain('background-image:')
    expect(mask).toContain('background-position: top, bottom;')
    expect(mask).toContain('background-repeat: no-repeat, no-repeat;')
    expect(mask).not.toMatch(/\n\s*background:\s/)
  })
})
