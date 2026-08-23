import { describe, expect, it } from 'vitest'
import registerSource from './index.vue?raw'
import authSource from '../auth/index.vue?raw'

/**
 * 源码断言。注册流程的文案改动是产品决定，不该被后来的顺手编辑撤回。
 */
describe('注册页文案', () => {
  it('不再出现「免费」', () => {
    expect(registerSource).not.toContain('免费')
    // 登录页的入口是注册页的唯一门口，留着「免费」会和它打开的页面自相矛盾。
    expect(authSource).not.toContain('免费')
  })

  it('页尾的说明块已移除', () => {
    expect(registerSource).not.toContain('资料仅用于档案匹配')
    expect(registerSource).not.toContain('会员为可选的增值服务')
    expect(registerSource).not.toContain('class="notice"')
  })

  it('提交按钮就叫「注册」', () => {
    expect(registerSource).toContain("loading ? '正在创建账号' : '注册'")
  })
})
