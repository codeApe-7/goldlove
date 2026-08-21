import { describe, expect, it } from 'vitest'
import {
  cascadeColumns,
  formatRegionValue,
  normalizeSelection,
  parseRegionValue,
  selectCity,
  selectDistrict,
  selectProvince,
} from './regionCascade'
import type { RegionNode } from './regionCascade'

const TREE: RegionNode[] = [
  { name: '北京市', children: [{ name: '北京市', children: [{ name: '东城区' }, { name: '朝阳区' }] }] },
  {
    name: '广东省',
    children: [
      { name: '广州市', children: [{ name: '天河区' }, { name: '越秀区' }] },
      { name: '深圳市', children: [{ name: '南山区' }, { name: '福田区' }] },
    ],
  },
]

describe('cascadeColumns', () => {
  it('三列内容跟随当前选择', () => {
    const columns = cascadeColumns(TREE, { province: '广东省', city: '深圳市', district: '南山区' })

    expect(columns.provinces).toEqual(['北京市', '广东省'])
    expect(columns.cities).toEqual(['广州市', '深圳市'])
    expect(columns.districts).toEqual(['南山区', '福田区'])
  })

  it('省份未选时市与区两列为空', () => {
    const columns = cascadeColumns(TREE, { province: '', city: '', district: '' })

    expect(columns.provinces).toHaveLength(2)
    expect(columns.cities).toEqual([])
    expect(columns.districts).toEqual([])
  })
})

describe('selectProvince', () => {
  it('换省份要把市与区一起重置到首项', () => {
    const next = selectProvince(TREE, '广东省')

    expect(next).toEqual({ province: '广东省', city: '广州市', district: '天河区' })
  })

  it('选同一个省份不动已选的市与区', () => {
    const current = { province: '广东省', city: '深圳市', district: '福田区' }

    expect(selectProvince(TREE, '广东省', current)).toEqual(current)
  })
})

describe('selectCity', () => {
  it('换城市要把区重置到首项', () => {
    const current = { province: '广东省', city: '广州市', district: '天河区' }

    expect(selectCity(TREE, current, '深圳市')).toEqual({
      province: '广东省',
      city: '深圳市',
      district: '南山区',
    })
  })
})

describe('selectDistrict', () => {
  it('只替换区县', () => {
    const current = { province: '广东省', city: '深圳市', district: '南山区' }

    expect(selectDistrict(current, '福田区')).toEqual({
      province: '广东省',
      city: '深圳市',
      district: '福田区',
    })
  })
})

describe('formatRegionValue', () => {
  it('省市区拼成三段', () => {
    expect(formatRegionValue({ province: '广东省', city: '深圳市', district: '南山区' }))
      .toBe('广东省 / 深圳市 / 南山区')
  })

  it('直辖市省市同名时省掉重复的一段', () => {
    expect(formatRegionValue({ province: '北京市', city: '北京市', district: '东城区' }))
      .toBe('北京市 / 东城区')
  })

  it('选择不完整时只拼已选部分', () => {
    expect(formatRegionValue({ province: '广东省', city: '', district: '' })).toBe('广东省')
    expect(formatRegionValue({ province: '', city: '', district: '' })).toBe('')
  })
})

describe('parseRegionValue', () => {
  it('解析三段值', () => {
    expect(parseRegionValue('广东省 / 深圳市 / 南山区')).toEqual({
      province: '广东省',
      city: '深圳市',
      district: '南山区',
    })
  })

  it('解析被折叠成两段的直辖市值', () => {
    expect(parseRegionValue('北京市 / 东城区')).toEqual({
      province: '北京市',
      city: '',
      district: '东城区',
    })
  })

  it('容忍没有空格的分隔符', () => {
    expect(parseRegionValue('广东省/深圳市/南山区')).toEqual({
      province: '广东省',
      city: '深圳市',
      district: '南山区',
    })
  })
})

describe('normalizeSelection', () => {
  it('两段值补出中间的城市', () => {
    expect(normalizeSelection(TREE, parseRegionValue('北京市 / 东城区'))).toEqual({
      province: '北京市',
      city: '北京市',
      district: '东城区',
    })
  })

  it('省份不在数据里时整体清空', () => {
    expect(normalizeSelection(TREE, { province: '火星省', city: '甲市', district: '乙区' }))
      .toEqual({ province: '', city: '', district: '' })
  })

  it('区县不属于当前城市时回落到该市首个区县', () => {
    expect(normalizeSelection(TREE, { province: '广东省', city: '深圳市', district: '天河区' }))
      .toEqual({ province: '广东省', city: '深圳市', district: '南山区' })
  })

  it('存量自由文本没有分隔符时不当作地区值', () => {
    expect(normalizeSelection(TREE, parseRegionValue('杭州'))).toEqual({
      province: '',
      city: '',
      district: '',
    })
  })
})
