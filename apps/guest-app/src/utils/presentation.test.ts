import { describe, expect, it } from 'vitest'
import {
  guestStatusMeta,
  isPrivateValue,
  MAX_LIFE_PHOTOS,
  PRIVATE_FIELD_VALUES,
  profileCompletion,
  profileGroup,
  remainingLifePhotoSlots,
} from './presentation'

describe('guest presentation helpers', () => {
  it('groups known fields and falls back to more', () => {
    expect(profileGroup('gender')).toBe('basic')
    expect(profileGroup('age')).toBe('basic')
    expect(profileGroup('occupation')).toBe('career')
    expect(profileGroup('income_range')).toBe('career')
    expect(profileGroup('wechat_id')).toBe('social')
    expect(profileGroup('douyin_id')).toBe('social')
    expect(profileGroup('favoriteBook')).toBe('more')
  })

  // 下线的字段要落到 more，而不是继续占着 basic / social 的位置。
  // 后端已经不下发它们，这里只是保证万一下发了也不会渲染进原来的分组。
  it('已下线的字段不再有专属分组', () => {
    expect(profileGroup('birth_date')).toBe('more')
    expect(profileGroup('douyin_nickname')).toBe('more')
    expect(profileGroup('douyin_profile_url')).toBe('more')
  })

  it('includes the required avatar in completion', () => {
    const definitions = [{ fieldCode: 'gender' }, { fieldCode: 'city' }]
    expect(profileCompletion(definitions, { gender: '女', city: '' }, false)).toBe(33)
    expect(profileCompletion(definitions, { gender: '女', city: '上海' }, true)).toBe(100)
  })

  it('treats false and zero as completed values', () => {
    const definitions = [{ fieldCode: 'acceptPets' }, { fieldCode: 'heightCm' }]
    expect(profileCompletion(definitions, { acceptPets: false, heightCm: 0 }, true)).toBe(100)
  })

  it('localizes review states', () => {
    expect(guestStatusMeta('PENDING_REVIEW').label).toBe('审核中')
    expect(guestStatusMeta('CHANGES_REQUESTED').tone).toBe('danger')
    expect(guestStatusMeta('APPROVED').label).toBe('已通过')
  })

  it('生活照上限 3 张，头像不占额度', () => {
    expect(MAX_LIFE_PHOTOS).toBe(3)
    expect(remainingLifePhotoSlots([{ category: 'AVATAR' }, { category: 'LIFE' }])).toBe(2)
    expect(remainingLifePhotoSlots(Array.from({ length: 3 }, () => ({ category: 'LIFE' })))).toBe(0)
    // 存量档案可能超过上限（上限是从 6 收下来的）——剩余额度不能变成负数，
    // 否则 choosePhotos 会拿着负数去要图。
    expect(remainingLifePhotoSlots(Array.from({ length: 6 }, () => ({ category: 'LIFE' })))).toBe(0)
  })
})

describe('isPrivateValue', () => {
  it('识别保密与不公开', () => {
    expect(isPrivateValue('保密')).toBe(true)
    expect(isPrivateValue('不公开')).toBe(true)
  })

  it('容忍首尾空白', () => {
    expect(isPrivateValue(' 保密 ')).toBe(true)
  })

  it('具体档位不算保密', () => {
    expect(isPrivateValue('30万-50万')).toBe(false)
    expect(isPrivateValue('20万以下')).toBe(false)
  })

  it('空值与非字符串不算保密', () => {
    expect(isPrivateValue('')).toBe(false)
    expect(isPrivateValue(null)).toBe(false)
    expect(isPrivateValue(undefined)).toBe(false)
    expect(isPrivateValue(30)).toBe(false)
  })

  it('保密值清单里不含具体档位', () => {
    expect(PRIVATE_FIELD_VALUES).toEqual(['保密', '不公开'])
  })
})
