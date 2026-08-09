import { describe, expect, it } from 'vitest'
import { validateProfileForm } from './profile'
import type { GuestFieldDefinition } from '@/types'

const definitions: GuestFieldDefinition[] = [
  {
    id: 1,
    fieldCode: 'gender',
    label: '性别',
    dataType: 'SINGLE_OPTION',
    required: true,
    options: ['男', '女'],
    sortOrder: 10,
    instructions: '',
  },
  {
    id: 2,
    fieldCode: 'bio',
    label: '自我介绍',
    dataType: 'TEXT',
    required: false,
    options: [],
    sortOrder: 20,
    instructions: '',
  },
]

describe('profile validator', () => {
  it('reports missing required fields', () => {
    const missing = validateProfileForm(definitions, { bio: '喜欢徒步' })
    expect(missing).toContain('gender')
    expect(missing).not.toContain('bio')
  })

  it('accepts complete values', () => {
    expect(validateProfileForm(definitions, { gender: '男', bio: '' })).toEqual([])
  })
})
