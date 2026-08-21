/** 省 / 市 / 区三级联动的纯状态推导。规范图 3.8 是三列并排列表。 */

export interface RegionNode {
  name: string
  children?: RegionNode[]
}

export interface CascadeSelection {
  province: string
  city: string
  district: string
}

export interface CascadeColumns {
  provinces: string[]
  cities: string[]
  districts: string[]
}

const SEPARATOR = ' / '
const EMPTY: CascadeSelection = { province: '', city: '', district: '' }

export function cascadeColumns(
  tree: readonly RegionNode[],
  selection: CascadeSelection,
): CascadeColumns {
  const province = childByName(tree, selection.province)
  const city = childByName(province?.children, selection.city)
  return {
    provinces: names(tree),
    cities: names(province?.children),
    districts: names(city?.children),
  }
}

export function selectProvince(
  tree: readonly RegionNode[],
  name: string,
  current?: CascadeSelection,
): CascadeSelection {
  if (current && current.province === name) {
    return current
  }
  const province = childByName(tree, name)
  const city = province?.children?.[0]
  return {
    province: province?.name ?? '',
    city: city?.name ?? '',
    district: city?.children?.[0]?.name ?? '',
  }
}

export function selectCity(
  tree: readonly RegionNode[],
  current: CascadeSelection,
  name: string,
): CascadeSelection {
  const province = childByName(tree, current.province)
  const city = childByName(province?.children, name)
  return {
    province: current.province,
    city: city?.name ?? '',
    district: city?.children?.[0]?.name ?? '',
  }
}

export function selectDistrict(
  current: CascadeSelection,
  name: string,
): CascadeSelection {
  return { ...current, district: name }
}

export function formatRegionValue(selection: CascadeSelection): string {
  const segments = [selection.province, selection.city, selection.district]
    .filter((segment) => segment !== '')
  // 直辖市的省与市同名，重复一段没有信息量。
  return segments.filter((segment, index) => segment !== segments[index - 1]).join(SEPARATOR)
}

export function parseRegionValue(value: string): CascadeSelection {
  const segments = value.split('/').map((segment) => segment.trim()).filter((segment) => segment !== '')
  if (segments.length >= 3) {
    return { province: segments[0], city: segments[1], district: segments[2] }
  }
  if (segments.length === 2) {
    // 折叠过的直辖市值，中间的市留空交给 normalizeSelection 补。
    return { province: segments[0], city: '', district: segments[1] }
  }
  if (segments.length === 1) {
    return { province: segments[0], city: '', district: '' }
  }
  return { ...EMPTY }
}

/**
 * 把可能残缺或过期的选择修正成数据里真实存在的组合。
 * 存量档案的所在城市是自由文本，对不上数据时整体清空，让用户重选。
 */
export function normalizeSelection(
  tree: readonly RegionNode[],
  selection: CascadeSelection,
): CascadeSelection {
  const province = childByName(tree, selection.province)
  if (!province) {
    return { ...EMPTY }
  }
  const city = childByName(province.children, selection.city)
    ?? province.children?.find((candidate) => childByName(candidate.children, selection.district))
    ?? province.children?.[0]
  const district = childByName(city?.children, selection.district) ?? city?.children?.[0]
  return {
    province: province.name,
    city: city?.name ?? '',
    district: district?.name ?? '',
  }
}

function childByName(
  nodes: readonly RegionNode[] | undefined,
  name: string,
): RegionNode | undefined {
  if (name === '') {
    return undefined
  }
  return nodes?.find((node) => node.name === name)
}

function names(nodes: readonly RegionNode[] | undefined): string[] {
  return nodes?.map((node) => node.name) ?? []
}
