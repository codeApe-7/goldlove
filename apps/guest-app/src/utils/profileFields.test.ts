import { describe, expect, it } from 'vitest'
import type { GuestProfileDraft } from '@/types'
import {
  draftToProfileValues,
  profileValuesToDraftPayload,
} from './profileFields'

const draft: GuestProfileDraft = {
  profileNo: 'PROFILE-001',
  status: 'DRAFT',
  version: 3,
  gender: '女',
  age: 31,
  heightCm: 168,
  education: '本科',
  occupation: '设计师',
  incomeRange: '30-50万',
  city: '上海市',
  wechatId: 'wx_demo',
  douyinId: 'dy_demo',
  missingRequiredFieldCodes: [],
  dynamicFields: [],
}

describe('profile field mapping', () => {
  it('hydrates definition-code values from the camelCase draft', () => {
    const values = draftToProfileValues(draft)

    expect(values.age).toBe(31)
    expect(values.wechat_id).toBe('wx_demo')
    expect(values.douyin_id).toBe('dy_demo')
  })

  // 出生日期与抖音昵称 / 主页链接已下线。映射表是这三处唯一的清单，
  // 漏删一项就会把一个后端已经不认的字段发上去。
  it('不再映射出生日期与已下线的抖音字段', () => {
    const values = draftToProfileValues(draft)

    expect(values).not.toHaveProperty('birth_date')
    expect(values).not.toHaveProperty('douyin_nickname')
    expect(values).not.toHaveProperty('douyin_profile_url')

    const payload = profileValuesToDraftPayload({ gender: '男' }, null)
    expect(payload).not.toHaveProperty('birthDate')
    expect(payload).not.toHaveProperty('douyinNickname')
    expect(payload).not.toHaveProperty('douyinProfileUrl')
  })

  it('creates a camelCase save payload with the expected version', () => {
    const payload = profileValuesToDraftPayload({
      gender: '男',
      wechat_id: 'wx_saved',
      age: 28,
    }, 7)

    expect(payload.expectedVersion).toBe(7)
    expect(payload.wechatId).toBe('wx_saved')
    expect(payload.age).toBe(28)
  })

  it('把没填的选填字段发成 null 而不是空串', () => {
    // 后端对空串与 null 的处理并不总是一致，而「没填」是这里唯一想表达的意思。
    const payload = profileValuesToDraftPayload({
      gender: '男',
      douyin_id: '',
      age: '',
    }, null)

    expect(payload.douyinId).toBeNull()
    expect(payload.age).toBeNull()
    // 完全没出现在表单里的字段同样是 null
    expect(payload.city).toBeNull()
    expect(Object.values(payload)).not.toContain('')
  })

  it('保留 0 与 false，它们是真实取值不是空', () => {
    const payload = profileValuesToDraftPayload({ height_cm: 0, douyin_id: false }, null)

    expect(payload.heightCm).toBe(0)
    expect(payload.douyinId).toBe(false)
  })
})
