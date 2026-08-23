import { describe, expect, it } from 'vitest'
import settingsSource from './PaymentSettingsView.vue?raw'
import layoutSource from '../layouts/AdminLayout.vue?raw'

/**
 * 源码断言，与 ProfilesView.test.ts 同一路子：锁住「决定过一次、不该被顺手改掉」的地方，
 * 渲染细节交给浏览器验收。金额换算本身在 utils/money.test.ts 里有真单测。
 */
describe('支付设置页', () => {
  it('界面按元录入，传输按分', () => {
    // 后端、数据库与支付渠道一律用分；只有这一层做换算，别处不要再换一次。
    expect(settingsSource).toContain('yuanToMinor(amountYuan.value')
    expect(settingsSource).toContain('minorToYuan(loaded.vipUpgradeAmountMinor)')
    expect(settingsSource).toContain('updatePaymentSetting(amountMinor)')
  })

  it('上下限取自后端下发的值', () => {
    // 前端抄一份 1 / 10000000 就会和库里的 CHECK 漂移。
    expect(settingsSource).toContain('setting.value?.minAmountMinor')
    expect(settingsSource).toContain('setting.value?.maxAmountMinor')
  })

  it('说清当前金额是后台设的还是服务器配置的', () => {
    expect(settingsSource).toContain('managedInAdmin')
    expect(settingsSource).toContain('服务器配置（后台尚未设置过）')
    expect(settingsSource).toContain('现在用的还是服务器配置值')
  })

  it('提示改价不追溯已创建的订单', () => {
    expect(settingsSource).toContain('改价只影响之后创建的订单')
  })

  it('金额还没被后台接管时，即使没改动也能保存', () => {
    // 后端只把「已有行且值相同」当空操作；没有行时保存的意义是把当前金额固定下来。
    // 前端如果一律按「值有没有变」置灰，这件事在界面上就做不到。
    expect(settingsSource).toContain('edited.value || !setting.value.managedInAdmin')
    expect(settingsSource).toContain(':disabled="!canSave"')
    expect(settingsSource).toContain('固定当前金额')
  })

  it('「撤销修改」只在真的改过值时可点', () => {
    expect(settingsSource).toContain(':disabled="!edited || saving"')
  })
})

describe('侧边栏（支付设置）', () => {
  it('支付设置落在配置组里', () => {
    expect(layoutSource).toContain("{ path: '/payment-settings', label: '支付设置', icon: Coin }")
  })
})
