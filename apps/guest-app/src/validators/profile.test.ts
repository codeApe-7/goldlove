import { describe, expect, it } from 'vitest'
import { validateProfileForm } from './profile'
import type { GuestFieldDefinition } from '@/types'

const definitions: GuestFieldDefinition[] = [
  {
    id: 1,
    fieldCode: 'gender',
    label: '性别',
    storageKind: 'CORE',
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
    storageKind: 'DYNAMIC',
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
    expect(missing).toContain('性别')
    expect(missing).not.toContain('自我介绍')
  })

  it('accepts complete values', () => {
    expect(validateProfileForm(definitions, { gender: '男', bio: '' })).toEqual([])
  })

  it('requires only WeChat among the social account fields', () => {
    const socialDefinitions: GuestFieldDefinition[] = [
      {
        id: 3,
        fieldCode: 'wechat_id',
        label: '微信号',
        storageKind: 'CORE',
        dataType: 'TEXT',
        required: true,
        options: [],
        sortOrder: 80,
        instructions: '请输入微信号',
      },
      {
        id: 4,
        fieldCode: 'douyin_id',
        label: '抖音号',
        storageKind: 'CORE',
        dataType: 'TEXT',
        required: false,
        options: [],
        sortOrder: 90,
        instructions: '选填，请输入抖音号',
      },
    ]

    expect(validateProfileForm(socialDefinitions, { douyin_id: '' })).toEqual(['微信号'])
  })
})
