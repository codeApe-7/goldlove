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
    expect(payload.douyinId).toBe('')
  })
})
