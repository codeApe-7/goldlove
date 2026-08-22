#!/usr/bin/env node
// 生成 src/data/regions.data.ts —— 全国三级行政区划。
//
// 用法（需要网络）：
//   node scripts/generate-regions.mjs
//
// 数据来源：
//   1. 大陆 31 个省级单位：modood/Administrative-divisions-of-China 的 dist/pca-code.json
//      （国家统计局区划代码派生，最广泛使用的一份）。
//   2. 香港 18 区与澳门 8 堂区：province-city-china 的 dist/data.json（pca-code.json 不含港澳台）。
//   3. 台湾省 22 个县市：本文件内置常量。两份数据源都只有台湾省一级，没有下级区划。
//
// 编码格式（每个省级单位一行字符串）：
//   省名|市名:区1,区2,…|市名:区1,…
// 选这个格式是因为嵌套字面量写出来有 200KB 且没法 diff；一行一省既紧凑又能看出改了哪个省。

import { writeFileSync } from 'node:fs'

const PCA_URL = 'https://raw.githubusercontent.com/modood/Administrative-divisions-of-China/master/dist/pca-code.json'
const SAR_URL = 'https://cdn.jsdelivr.net/npm/province-city-china@8/dist/data.json'

// 直辖市与特别行政区在数据里的中间层是「市辖区」这类通用名，级联选择器里显示成
// 「北京市 / 市辖区 / 东城区」很别扭。统一改成省名自身，formatRegionValue 会把重复段折叠掉。
const SINGLE_TIER_PROVINCES = new Set(['北京市', '天津市', '上海市', '重庆市'])

// 台湾省的县级单位。两份数据源都没有台湾的下级区划，这里只收录 22 个县市，
// 不臆造乡镇一级；结构上与直辖市一致（中间层为省名，第三层是真实的县市级单位）。
const TAIWAN_COUNTIES = [
  '台北市', '新北市', '桃园市', '台中市', '台南市', '高雄市',
  '基隆市', '新竹市', '嘉义市',
  '新竹县', '苗栗县', '彰化县', '南投县', '云林县', '嘉义县',
  '屏东县', '宜兰县', '花莲县', '台东县', '澎湖县', '金门县', '连江县',
]

async function fetchJson(url) {
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`拉取失败 ${response.status}: ${url}`)
  }
  return response.json()
}

/** 把 pca-code.json 的省 → 市 → 区树转成 {name, cities:[{name, districts:[]}]}。 */
function fromPca(provinces) {
  return provinces.map((province) => {
    const cities = province.children ?? []
    if (SINGLE_TIER_PROVINCES.has(province.name)) {
      // 重庆同时有「市辖区」和「县」两个分组，合并成一个以省名命名的节点。
      const districts = cities.flatMap((city) => (city.children ?? []).map((d) => d.name))
      return { name: province.name, cities: [{ name: province.name, districts }] }
    }
    return {
      name: province.name,
      cities: cities.map((city) => ({
        name: city.name,
        districts: (city.children ?? []).map((district) => district.name),
      })),
    }
  })
}

/** 从扁平数据里抽出一个省级单位的县级列表（city 为分组占位，只取 area 层）。 */
function sarFromFlat(flat, provinceName) {
  const head = flat.find((row) => row.name === provinceName && row.city === 0 && row.area === 0)
  if (!head) {
    throw new Error(`扁平数据里找不到 ${provinceName}`)
  }
  const districts = flat
    .filter((row) => row.province === head.province && row.area !== 0 && row.town === 0)
    .map((row) => row.name)
  if (districts.length === 0) {
    throw new Error(`${provinceName} 没有下级区划`)
  }
  return { name: provinceName, cities: [{ name: provinceName, districts }] }
}

function encode(province) {
  const cities = province.cities
    .map((city) => `${city.name}:${city.districts.join(',')}`)
    .join('|')
  return `${province.name}|${cities}`
}

function assertClean(provinces) {
  for (const province of provinces) {
    if (province.cities.length === 0) {
      throw new Error(`${province.name} 没有城市`)
    }
    for (const city of province.cities) {
      if (city.districts.length === 0) {
        throw new Error(`${province.name} ${city.name} 没有区县`)
      }
    }
    // 分隔符出现在名字里会让解码错位。
    const flatNames = [province.name, ...province.cities.flatMap((c) => [c.name, ...c.districts])]
    for (const name of flatNames) {
      if (/[|:,;]/.test(name)) {
        throw new Error(`名称含分隔符，编码会错位: ${name}`)
      }
    }
  }
}

const [pca, flat] = await Promise.all([fetchJson(PCA_URL), fetchJson(SAR_URL)])

const provinces = [
  ...fromPca(pca),
  { name: '台湾省', cities: [{ name: '台湾省', districts: TAIWAN_COUNTIES }] },
  sarFromFlat(flat, '香港特别行政区'),
  sarFromFlat(flat, '澳门特别行政区'),
]

assertClean(provinces)

const counts = provinces.reduce(
  (acc, province) => ({
    provinces: acc.provinces + 1,
    cities: acc.cities + province.cities.length,
    districts: acc.districts + province.cities.reduce((s, c) => s + c.districts.length, 0),
  }),
  { provinces: 0, cities: 0, districts: 0 },
)

const body = provinces.map((province) => `  '${encode(province)}',`).join('\n')
const file = `// 本文件由 scripts/generate-regions.mjs 生成，请勿手改。
// 数据来源：大陆取国家统计局区划代码派生数据集，港澳取 province-city-china，
// 台湾省只收录 22 个县市（数据源均无台湾下级区划，不臆造乡镇一级）。
//
// 编码：省名|市名:区1,区2,…|市名:区1,…
// 直辖市与港澳台的中间层统一用省名自身，展示时由 formatRegionValue 折叠掉重复段。
//
// 规模：${counts.provinces} 个省级、${counts.cities} 个地级、${counts.districts} 个县级。

export const ENCODED_REGIONS: readonly string[] = [
${body}
]
`

// 两个前端都要这份数据（访客端填档案、后台按地区筛选），仓库里没有共享包，
// 所以由生成脚本同时写两份——手动复制迟早会漂移。
const targets = [
  new URL('../src/data/regions.data.ts', import.meta.url),
  new URL('../../admin-web/src/data/regions.data.ts', import.meta.url),
]
for (const target of targets) {
  writeFileSync(target, file, 'utf8')
}
console.log(
  `已生成 ${targets.length} 份 regions.data.ts：省级 ${counts.provinces}、地级 ${counts.cities}、县级 ${counts.districts}`,
)
