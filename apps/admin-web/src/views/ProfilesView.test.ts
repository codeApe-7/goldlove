import { describe, expect, it } from 'vitest'
import profilesSource from './ProfilesView.vue?raw'
import fieldsSource from './FieldDefinitionsView.vue?raw'
import drawerSource from '../components/ProfileDetailDrawer.vue?raw'
import layoutSource from '../layouts/AdminLayout.vue?raw'

/**
 * 源码断言。这几条锁的是「决定过一次、不该被顺手改掉」的行为，
 * 渲染细节交给浏览器验收，这里只挡住语义漂移。
 */
describe('档案管理页', () => {
  it('五个 tab 各自对应的筛选条件', () => {
    expect(profilesSource).toContain("all: {}")
    expect(profilesSource).toContain("draft: { status: 'DRAFT' }")
    expect(profilesSource).toContain("completed: { status: 'COMPLETED' }")
    expect(profilesSource).toContain("suspended: { accountStatus: 'SUSPENDED' }")
    // 「已付费会员」= VIP 或 SVIP，单个 membershipTier 表达不了，必须走 paidOnly。
    expect(profilesSource).toContain('paid: { paidOnly: true }')
  })

  it('完成度与账号状态是两列，不合并', () => {
    // 停用是账号级别的、完成度是档案级别的，挤进一列会丢掉「停用前填完了没」。
    expect(profilesSource).toContain('label="完成度"')
    expect(profilesSource).toContain('label="账号状态"')
    expect(profilesSource).toContain('profileStatusMeta(row.status)')
    expect(profilesSource).toContain('accountStatusMeta(row.accountStatus)')
  })

  it('批量操作只有导出', () => {
    expect(profilesSource).toContain('批量导出')
    // 批量停用 / 批量授权 / 批量删除是明确不做的：
    // 授权会绕过支付台账，删除则受数据库权限限制（archive_app 没有 DELETE）。
    expect(profilesSource).not.toContain('批量停用')
    expect(profilesSource).not.toContain('批量授权')
    expect(profilesSource).not.toContain('批量删除')
  })

  it('工具栏主操作是生成激活码，而不是新建档案', () => {
    expect(profilesSource).toContain('生成激活码')
    expect(profilesSource).not.toContain('新建档案')
    expect(profilesSource).toContain("name: 'activation-codes', query: { generate: '1' }")
  })

  it('创建时间上界推到当天最后一刻', () => {
    // 日期选择器给的是本地零点，直接当上界会漏掉结束日期那一整天。
    expect(profilesSource).toContain('query.createdFrom = startOfDay(from).toISOString()')
    expect(profilesSource).toContain('query.createdTo = endOfDay(to).toISOString()')
    expect(profilesSource).toContain('copy.setHours(23, 59, 59, 999)')
  })

  it('计数用的是不含 tab 条件的筛选', () => {
    expect(profilesSource).toContain('profileCounts(baseQuery())')
    expect(profilesSource).toContain('...baseQuery(), ...TAB_FILTERS[tab.value]')
  })
})

describe('档案详情抽屉', () => {
  /**
   * 档案只收年龄，不收出生日期，抖音只留账号本身。
   *
   * 这三项都是能直接指认到人的信息，后端连列都删了。断言抽屉里**没有**这些标签：
   * 加一行 `{ label: '出生日期', ... }` 不会有任何类型错误，
   * 只会渲染出一个永远是「—」的格子，没人会注意到。
   */
  it('只显示年龄，没有出生日期与已下线的抖音字段', () => {
    expect(drawerSource).toContain("{ label: '年龄', value: ageLabel(value.age) }")
    expect(drawerSource).toContain("{ label: '抖音号', value: orDash(value.douyinId) }")
    expect(drawerSource).not.toContain('出生日期')
    expect(drawerSource).not.toContain('抖音昵称')
    expect(drawerSource).not.toContain('抖音主页')
  })
})

describe('字段配置页', () => {
  it('核心字段的必填与启用置灰并原样回传', () => {
    // 后端对 CORE 改这两项直接抛 FIELD_DEFINITION_IMMUTABLE，不能让人点完再吃 409。
    expect(fieldsSource).toContain("coreLocked = computed(() => editing.value?.storageKind === 'CORE')")
    expect(fieldsSource).toContain('required: coreLocked.value ? editing.value.required : form.required')
    expect(fieldsSource).toContain('enabled: coreLocked.value ? editing.value.enabled : form.enabled')
    expect(fieldsSource).toContain(':disabled="coreLocked"')
  })

  it('新建 / 编辑用抽屉而不是弹窗（规范图 7.1）', () => {
    expect(fieldsSource).toContain('<el-drawer')
    expect(fieldsSource).not.toContain('<el-dialog')
  })
})

describe('侧边栏', () => {
  it('八个真实页面分成三组', () => {
    expect(layoutSource).toContain("title: '数据'")
    expect(layoutSource).toContain("title: '运营'")
    expect(layoutSource).toContain("title: '配置'")
    // 标签管理 / 系统设置 / 日志中心本期没有后端，不放空菜单。
    expect(layoutSource).not.toContain('标签管理')
    expect(layoutSource).not.toContain('系统设置')
    expect(layoutSource).not.toContain('日志中心')
  })

  it('课程分类叫「课程目录」', () => {
    expect(layoutSource).toContain("label: '课程目录'")
    expect(layoutSource).not.toContain('课程合集')
  })

  it('选中态用主色，金色只留给品牌标识', () => {
    expect(layoutSource).toContain('background: var(--ds-primary);')
    expect(layoutSource).not.toContain('--archive-gold')
  })
})
