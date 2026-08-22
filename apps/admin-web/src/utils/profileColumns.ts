/**
 * 档案列表的列显隐（规范图 4 的「列设置」）。
 *
 * 只存「哪些可选列被关掉了」而不是完整列表：这样以后新增列默认可见，
 * 不会因为某个管理员浏览器里存着一份旧配置就看不到新列。
 */
export interface ProfileColumn {
  key: string
  label: string
  /** 默认关掉。列太多会把表格挤到横向滚动，右侧固定的操作列会压住内容。 */
  defaultHidden?: boolean
}

/** 身份、状态与操作列不可关——关掉之后这张表就没法用了。 */
export const OPTIONAL_PROFILE_COLUMNS: readonly ProfileColumn[] = [
  { key: 'gender', label: '性别' },
  { key: 'age', label: '年龄' },
  { key: 'city', label: '所在地区' },
  { key: 'membershipTier', label: '会员等级' },
  { key: 'createdAt', label: '创建时间' },
  { key: 'updatedAt', label: '更新时间', defaultHidden: true },
]

const STORAGE_KEY = 'admin-profile-hidden-columns'

/** 没存过配置时的可见列。 */
export function defaultVisibleColumns(): string[] {
  return OPTIONAL_PROFILE_COLUMNS
    .filter((column) => !column.defaultHidden)
    .map((column) => column.key)
}

/**
 * 当前可见列。存过配置就按配置（含用户主动打开的默认隐藏列），
 * 没存过则用默认集合。
 */
export function visibleColumns(storage: Pick<Storage, 'getItem'>): string[] {
  const raw = safely(() => storage.getItem(STORAGE_KEY))
  if (!raw) {
    return defaultVisibleColumns()
  }
  const hidden = new Set(loadHiddenColumns(storage))
  return OPTIONAL_PROFILE_COLUMNS
    .map((column) => column.key)
    .filter((key) => !hidden.has(key))
}

export function loadHiddenColumns(storage: Pick<Storage, 'getItem'>): string[] {
  const raw = safely(() => storage.getItem(STORAGE_KEY))
  if (!raw) {
    return []
  }
  const parsed = safely(() => JSON.parse(raw) as unknown)
  if (!Array.isArray(parsed)) {
    return []
  }
  const known = new Set(OPTIONAL_PROFILE_COLUMNS.map((column) => column.key))
  return parsed.filter((key): key is string => typeof key === 'string' && known.has(key))
}

export function saveHiddenColumns(
  storage: Pick<Storage, 'setItem'>,
  hidden: readonly string[],
): void {
  safely(() => storage.setItem(STORAGE_KEY, JSON.stringify([...hidden])))
}

/** 从「可见列集合」反推要存的隐藏列，供列设置面板直接使用。 */
export function hiddenFromVisible(visible: readonly string[]): string[] {
  const shown = new Set(visible)
  return OPTIONAL_PROFILE_COLUMNS
    .map((column) => column.key)
    .filter((key) => !shown.has(key))
}

/**
 * 存储访问全部包在 try/catch 里：隐身窗口、被策略禁掉站点数据的浏览器
 * 读写 localStorage 会直接抛异常，列设置读不到不该让整张列表打不开。
 */
function safely<T>(action: () => T): T | null {
  try {
    return action()
  } catch {
    return null
  }
}
