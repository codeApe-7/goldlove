import { describe, expect, it } from 'vitest'
import {
  OPTIONAL_PROFILE_COLUMNS,
  defaultVisibleColumns,
  hiddenFromVisible,
  loadHiddenColumns,
  saveHiddenColumns,
  visibleColumns,
} from './profileColumns'

function fakeStorage(initial: Record<string, string> = {}) {
  const store = { ...initial }
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = value
    },
    read: () => store,
  }
}

describe('档案列表的列显隐', () => {
  it('没有存过配置时全部可选列都可见', () => {
    expect(loadHiddenColumns(fakeStorage())).toEqual([])
  })

  it('存下来的隐藏列能读回来', () => {
    const storage = fakeStorage()
    saveHiddenColumns(storage, ['gender', 'age'])
    expect(loadHiddenColumns(storage)).toEqual(['gender', 'age'])
  })

  it('忽略不认识的列名', () => {
    // 以后删掉某个列时，管理员浏览器里存的旧列名不该让读取整体失败。
    const storage = fakeStorage({
      'admin-profile-hidden-columns': '["gender","matchmaker"]',
    })
    expect(loadHiddenColumns(storage)).toEqual(['gender'])
  })

  it('存的内容不是数组或不是 JSON 时退回全部可见', () => {
    expect(loadHiddenColumns(fakeStorage({ 'admin-profile-hidden-columns': '{}' }))).toEqual([])
    expect(loadHiddenColumns(fakeStorage({ 'admin-profile-hidden-columns': '坏数据' }))).toEqual([])
  })

  it('存储抛异常时不炸——隐身窗口读写会直接 throw', () => {
    const throwing = {
      getItem: () => {
        throw new Error('SecurityError')
      },
      setItem: () => {
        throw new Error('SecurityError')
      },
    }
    expect(loadHiddenColumns(throwing)).toEqual([])
    expect(() => saveHiddenColumns(throwing, ['gender'])).not.toThrow()
  })

  it('从可见列反推隐藏列', () => {
    const all = OPTIONAL_PROFILE_COLUMNS.map((column) => column.key)
    expect(hiddenFromVisible(all)).toEqual([])
    expect(hiddenFromVisible(['gender'])).toEqual(all.filter((key) => key !== 'gender'))
  })

  it('没存过配置时更新时间默认关掉', () => {
    // 11 列在 1440 宽下会把表格挤到横向滚动，右侧固定的操作列会压住内容。
    expect(defaultVisibleColumns()).not.toContain('updatedAt')
    expect(defaultVisibleColumns()).toContain('createdAt')
    expect(visibleColumns(fakeStorage())).toEqual(defaultVisibleColumns())
  })

  it('存过配置后默认隐藏列也能被用户打开', () => {
    const storage = fakeStorage()
    saveHiddenColumns(storage, [])
    expect(visibleColumns(storage)).toContain('updatedAt')
  })
})
