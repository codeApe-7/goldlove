import { describe, expect, it } from 'vitest'
import { addCustomTag, availableTags, removeTag, toggleTag } from './tagSelection'

const CATALOG = ['健身', '旅行', '阅读', '摄影', '音乐', '美食']

describe('toggleTag', () => {
  it('未选中则追加到末尾', () => {
    expect(toggleTag(['健身'], '旅行')).toEqual(['健身', '旅行'])
  })

  it('已选中则移除', () => {
    expect(toggleTag(['健身', '旅行'], '健身')).toEqual(['旅行'])
  })

  it('不修改传入的数组', () => {
    const selected = ['健身']
    toggleTag(selected, '旅行')
    expect(selected).toEqual(['健身'])
  })
})

describe('removeTag', () => {
  it('移除指定标签', () => {
    expect(removeTag(['健身', '旅行', '阅读'], '旅行')).toEqual(['健身', '阅读'])
  })

  it('标签不存在时原样返回', () => {
    expect(removeTag(['健身'], '旅行')).toEqual(['健身'])
  })
})

describe('availableTags', () => {
  it('过滤掉已选项', () => {
    expect(availableTags(CATALOG, ['健身', '摄影'])).toEqual(['旅行', '阅读', '音乐', '美食'])
  })
})

describe('addCustomTag', () => {
  it('去掉首尾空白后加入', () => {
    expect(addCustomTag(['健身'], '  羽毛球 ', 8)).toEqual({
      selected: ['健身', '羽毛球'],
      error: '',
    })
  })

  it('空白输入被拒绝', () => {
    expect(addCustomTag(['健身'], '   ', 8)).toEqual({
      selected: ['健身'],
      error: '请输入标签内容',
    })
  })

  it('重复标签被拒绝', () => {
    expect(addCustomTag(['健身'], '健身', 8)).toEqual({
      selected: ['健身'],
      error: '该标签已存在',
    })
  })

  it('超出数量上限被拒绝', () => {
    expect(addCustomTag(['一', '二'], '三', 2)).toEqual({
      selected: ['一', '二'],
      error: '最多只能选 2 个标签',
    })
  })

  it('过长标签被拒绝', () => {
    expect(addCustomTag([], '这是一个非常非常冗长的兴趣爱好标签', 8).error)
      .toBe('单个标签最多 8 个字')
  })
})
