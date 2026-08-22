import { describe, expect, it } from 'vitest'
import { REGION_OPTIONS, formatRegionPath } from './regions'

describe('后台区划数据', () => {
  it('解码出 34 个省级单位并带上下级', () => {
    expect(REGION_OPTIONS).toHaveLength(34)
    const cities = REGION_OPTIONS.flatMap((province) => province.children ?? [])
    expect(cities).toHaveLength(344)
    const districts = cities.flatMap((city) => city.children ?? [])
    expect(districts).toHaveLength(3104)
  })

  it('每一级的 value 与 label 相同——档案里存的是文本，不是区划码', () => {
    const beijing = REGION_OPTIONS.find((province) => province.value === '北京市')
    expect(beijing?.label).toBe('北京市')
    expect(beijing?.children?.[0].value).toBe('北京市')
    expect(beijing?.children?.[0].children?.[0].value).toBe('东城区')
  })

  it('拼出的前缀与访客端存库格式一致', () => {
    expect(formatRegionPath(['广东省', '深圳市', '南山区'])).toBe('广东省 / 深圳市 / 南山区')
    expect(formatRegionPath(['广东省', '深圳市'])).toBe('广东省 / 深圳市')
    expect(formatRegionPath(['广东省'])).toBe('广东省')
  })

  it('折叠直辖市重复的省市段', () => {
    // 访客端存的是「北京市 / 东城区」，不折叠就前缀对不上，一条都筛不出来。
    expect(formatRegionPath(['北京市', '北京市', '东城区'])).toBe('北京市 / 东城区')
    expect(formatRegionPath(['北京市', '北京市'])).toBe('北京市')
  })

  it('台湾省只到县市一级', () => {
    // 港澳台与直辖市一样，中间层是省名自身，22 个县市挂在第三级；
    // 两份数据源都没有台湾的乡镇一级，不臆造。
    const taiwan = REGION_OPTIONS.find((province) => province.value === '台湾省')
    expect(taiwan?.children).toHaveLength(1)
    expect(taiwan?.children?.[0].value).toBe('台湾省')
    expect(taiwan?.children?.[0].children).toHaveLength(22)
  })
})
