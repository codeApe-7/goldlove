import { describe, expect, it } from 'vitest'
import collectionsSource from './CourseCollectionsView.vue?raw'

describe('课程目录页', () => {
  it('没有删除入口——目录下面挂着课程', () => {
    // 后端没给运行账号 DELETE 权限（V6 的 GRANT 段刻意不给），删了会留孤儿课程。
    // 界面上如果放个删除按钮，点下去只会吃一个 403。
    expect(collectionsSource).not.toContain('deleteCourseCollection')
    expect(collectionsSource).not.toContain('删除目录')
  })

  it('下线走隐藏，并说清访客端的后果', () => {
    expect(collectionsSource).toContain("'HIDDEN'")
    expect(collectionsSource).toContain('目录已隐藏，访客端不再展示')
  })

  it('课程数分「已发布 / 全部」两个口径', () => {
    // 运营判断「能不能下线」看总数，判断「现在有几节可看」看已发布数。
    expect(collectionsSource).toContain('row.publishedCourseCount')
    expect(collectionsSource).toContain('row.courseCount')
  })

  it('新增时排序自动排到最后，不用运营自己想数字', () => {
    expect(collectionsSource).toContain('Math.max(max, item.sortOrder), 0) + 10')
  })

  it('空状态说清为什么空', () => {
    expect(collectionsSource).toContain('课程必须挂在某个目录下')
  })
})
