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
  birthDate: '1995-05-20',
  heightCm: 168,
  education: '本科',
  occupation: '设计师',
  incomeRange: '30-50万',
  city: '上海市',
  wechatId: 'wx_demo',
  douyinId: 'dy_demo',
  douyinNickname: '晚风轻语',
  douyinProfileUrl: 'https://www.douyin.com/user/demo',
  missingRequiredFieldCodes: [],
  dynamicFields: [],
}

describe('profile field mapping', () => {
  it('hydrates definition-code values from the camelCase draft', () => {
    const values = draftToProfileValues(draft)

    expect(values.birth_date).toBe('1995-05-20')
    expect(values.wechat_id).toBe('wx_demo')
    expect(values.douyin_profile_url).toBe('https://www.douyin.com/user/demo')
  })

  it('creates a camelCase save payload with the expected version', () => {
    const payload = profileValuesToDraftPayload({
      gender: '男',
      wechat_id: 'wx_saved',
      douyin_nickname: '档案昵称',
    }, 7)

    expect(payload.expectedVersion).toBe(7)
    expect(payload.wechatId).toBe('wx_saved')
    expect(payload.douyinNickname).toBe('档案昵称')
  })

  it('把没填的选填字段发成 null 而不是空串', () => {
    // 后端 douyinProfileUrl 是 URI 类型，Jackson 把空串变成 URI.create("")
    // 而不是 null，于是选填的抖音主页链接会被当成「有值但格式不对」拒掉，
    // 用户根本存不了档案。空串一律转 null。
    const payload = profileValuesToDraftPayload({
      gender: '男',
      douyin_id: '',
      douyin_nickname: '',
      douyin_profile_url: '',
    }, null)

    expect(payload.douyinId).toBeNull()
    expect(payload.douyinNickname).toBeNull()
    expect(payload.douyinProfileUrl).toBeNull()
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
