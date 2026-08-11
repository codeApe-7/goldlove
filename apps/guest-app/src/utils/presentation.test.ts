import { describe, expect, it } from 'vitest'
import {
  guestStatusMeta,
  profileCompletion,
  profileGroup,
  remainingLifePhotoSlots,
} from './presentation'

describe('guest presentation helpers', () => {
  it('groups known fields and falls back to more', () => {
    expect(profileGroup('gender')).toBe('basic')
    expect(profileGroup('birth_date')).toBe('basic')
    expect(profileGroup('occupation')).toBe('career')
    expect(profileGroup('income_range')).toBe('career')
    expect(profileGroup('wechat_id')).toBe('social')
    expect(profileGroup('douyin_profile_url')).toBe('social')
    expect(profileGroup('favoriteBook')).toBe('more')
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

  it('counts only life photos against the six-photo limit', () => {
    expect(remainingLifePhotoSlots([{ category: 'AVATAR' }, { category: 'LIFE' }])).toBe(5)
    expect(remainingLifePhotoSlots(Array.from({ length: 7 }, () => ({ category: 'LIFE' })))).toBe(0)
  })
})
