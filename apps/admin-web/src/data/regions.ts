import { ENCODED_REGIONS } from './regions.data'

/**
 * 全国三级行政区划，解码成 Element Plus 级联选择器要的 { value, label, children }。
 *
 * 数据文件 regions.data.ts 由 apps/guest-app/scripts/generate-regions.mjs 生成，
 * 脚本会同时写访客端与后台两份，别手改任何一份。
 */
export interface RegionOption {
  value: string
  label: string
  children?: RegionOption[]
}

const SEPARATOR = ' / '

export const REGION_OPTIONS: RegionOption[] = ENCODED_REGIONS.map(decodeProvince)

/**
 * 把级联选中的路径拼成档案里存的那种文本前缀。
 *
 * 档案的 city 列存的是访客端 formatRegionValue 的产物：段与段用 ' / ' 连接，
 * 且直辖市重复的省市段会被折叠（北京市 / 北京市 / 东城区 → 北京市 / 东城区）。
 * 筛选走前缀匹配，所以这里必须用同一套折叠规则，否则选了「北京市」一条都筛不出来。
 */
export function formatRegionPath(path: readonly string[]): string {
  const segments = path.filter((segment) => segment !== '')
  return segments
    .filter((segment, index) => segment !== segments[index - 1])
    .join(SEPARATOR)
}

function decodeProvince(line: string): RegionOption {
  const [name, ...citySegments] = line.split('|')
  return { value: name, label: name, children: citySegments.map(decodeCity) }
}

function decodeCity(segment: string): RegionOption {
  const separator = segment.indexOf(':')
  const name = segment.slice(0, separator)
  return {
    value: name,
    label: name,
    children: segment
      .slice(separator + 1)
      .split(',')
      .map((district) => ({ value: district, label: district })),
  }
}
