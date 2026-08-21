import { describe, expect, it } from 'vitest'
import { REGIONS } from './regions'
import { cascadeColumns, formatRegionValue, normalizeSelection, selectProvince } from '@/utils/regionCascade'

describe('全国省市区数据集', () => {
  it('收录 34 个省级单位，含港澳台', () => {
    expect(REGIONS).toHaveLength(34)
    const names = REGIONS.map((province) => province.name)
    expect(names).toContain('台湾省')
    expect(names).toContain('香港特别行政区')
    expect(names).toContain('澳门特别行政区')
    expect(names).toContain('新疆维吾尔自治区')
    expect(names).toContain('内蒙古自治区')
  })

  it('地级与县级规模符合生成时的统计', () => {
    const cities = REGIONS.flatMap((province) => province.children ?? [])
    const districts = cities.flatMap((city) => city.children ?? [])
    expect(cities).toHaveLength(344)
    expect(districts).toHaveLength(3104)
  })

  it('每个省至少有一个城市，每个城市至少有一个区县', () => {
    // 空的第三列会让级联选择器点不下去，这是编码 / 解码错位的兜底校验。
    for (const province of REGIONS) {
      expect(province.children?.length, `${province.name} 没有城市`).toBeGreaterThan(0)
      for (const city of province.children ?? []) {
        expect(city.children?.length, `${province.name} ${city.name} 没有区县`).toBeGreaterThan(0)
      }
    }
  })

  it('没有重名的省份', () => {
    const names = REGIONS.map((province) => province.name)
    expect(new Set(names).size).toBe(names.length)
  })

  it('解码没有把分隔符残留进名称里', () => {
    const everyName = REGIONS.flatMap((province) => [
      province.name,
      ...(province.children ?? []).flatMap((city) => [
        city.name,
        ...(city.children ?? []).map((district) => district.name),
      ]),
    ])
    expect(everyName.filter((name) => /[|:,]/.test(name) || name === '')).toEqual([])
  })
})

describe('单层省级单位的中间层折叠', () => {
  it.each(['北京市', '天津市', '上海市', '重庆市', '台湾省', '香港特别行政区', '澳门特别行政区'])(
    '%s 的中间层是省名自身',
    (name) => {
      const province = REGIONS.find((item) => item.name === name)
      expect(province?.children).toHaveLength(1)
      expect(province?.children?.[0].name).toBe(name)
    },
  )

  it('直辖市展示时折叠掉重复的一段', () => {
    expect(formatRegionValue(selectProvince(REGIONS, '北京市'))).toBe('北京市 / 东城区')
  })

  it('重庆把市辖区与县合并进同一个节点', () => {
    const chongqing = REGIONS.find((item) => item.name === '重庆市')
    expect(chongqing?.children?.[0].children).toHaveLength(38)
  })

  it('台湾省只收录县市一级，不臆造乡镇', () => {
    const taiwan = REGIONS.find((item) => item.name === '台湾省')
    const counties = taiwan?.children?.[0].children ?? []
    expect(counties).toHaveLength(22)
    expect(counties.map((county) => county.name)).toContain('台北市')
    expect(counties.map((county) => county.name)).toContain('连江县')
  })
})

describe('数据集接入联动逻辑', () => {
  it('选省份后三列都能立即填满', () => {
    const selection = selectProvince(REGIONS, '广东省')
    const columns = cascadeColumns(REGIONS, selection)

    expect(selection).toEqual({ province: '广东省', city: '广州市', district: '荔湾区' })
    expect(columns.provinces).toHaveLength(34)
    expect(columns.cities).toHaveLength(21)
    expect(columns.cities).toContain('深圳市')
    expect(columns.districts).toContain('天河区')
  })

  it('能定位到不设区地级市的区县', () => {
    // 东莞、中山在统计局数据里以「市辖区」为分组，折叠后仍要能选到街道级单位。
    const guangdong = REGIONS.find((item) => item.name === '广东省')
    expect(guangdong?.children?.map((city) => city.name)).toContain('东莞市')
  })

  it('存量自由文本地址会被清空而不是留下半个选择', () => {
    expect(normalizeSelection(REGIONS, { province: '杭州', city: '', district: '' }))
      .toEqual({ province: '', city: '', district: '' })
  })

  it('三段值能原样还原', () => {
    const selection = normalizeSelection(REGIONS, {
      province: '浙江省',
      city: '杭州市',
      district: '西湖区',
    })
    expect(formatRegionValue(selection)).toBe('浙江省 / 杭州市 / 西湖区')
  })
})
