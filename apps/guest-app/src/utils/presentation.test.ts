import { describe, expect, it } from 'vitest'
import { guestStatusMeta, profileCompletion, profileGroup } from './presentation'

describe('guest presentation helpers', () => {
  it('groups known fields and falls back to more', () => {
    expect(profileGroup('gender')).toBe('basic')
    expect(profileGroup('occupation')).toBe('career')
    expect(profileGroup('wechatId')).toBe('social')
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
})
