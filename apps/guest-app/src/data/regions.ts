import { ENCODED_REGIONS } from './regions.data'
import type { RegionNode } from '@/utils/regionCascade'

/**
 * 全国三级行政区划：34 个省级单位（含港澳台）、344 个地级、3104 个县级。
 *
 * 数据在 regions.data.ts，由 scripts/generate-regions.mjs 从国家统计局派生数据集生成，
 * 编码成「省名|市名:区1,区2,…」的紧凑格式——嵌套字面量写出来有 200KB 且 diff 不可读。
 * 直辖市与港澳台的中间层是省名自身，展示时由 formatRegionValue 折叠掉重复段
 * （北京市 / 北京市 / 东城区 → 「北京市 / 东城区」）。
 */
export const REGIONS: RegionNode[] = ENCODED_REGIONS.map(decodeProvince)

function decodeProvince(line: string): RegionNode {
  const [name, ...citySegments] = line.split('|')
  return { name, children: citySegments.map(decodeCity) }
}

function decodeCity(segment: string): RegionNode {
  const separator = segment.indexOf(':')
  return {
    name: segment.slice(0, separator),
    children: segment
      .slice(separator + 1)
      .split(',')
      .map((district) => ({ name: district })),
  }
}
