/** 多选标签的增删与自定义标签校验。规范图 3.11。 */

const MAX_TAG_LENGTH = 8

export function toggleTag(selected: readonly string[], tag: string): string[] {
  return selected.includes(tag) ? removeTag(selected, tag) : [...selected, tag]
}

export function removeTag(selected: readonly string[], tag: string): string[] {
  return selected.filter((item) => item !== tag)
}

export function availableTags(
  catalog: readonly string[],
  selected: readonly string[],
): string[] {
  return catalog.filter((tag) => !selected.includes(tag))
}

export function addCustomTag(
  selected: readonly string[],
  raw: string,
  limit: number,
): { selected: string[]; error: string } {
  const tag = raw.trim()
  const unchanged = [...selected]
  if (tag === '') {
    return { selected: unchanged, error: '请输入标签内容' }
  }
  if (tag.length > MAX_TAG_LENGTH) {
    return { selected: unchanged, error: `单个标签最多 ${MAX_TAG_LENGTH} 个字` }
  }
  if (selected.includes(tag)) {
    return { selected: unchanged, error: '该标签已存在' }
  }
  if (selected.length >= limit) {
    return { selected: unchanged, error: `最多只能选 ${limit} 个标签` }
  }
  return { selected: [...selected, tag], error: '' }
}
