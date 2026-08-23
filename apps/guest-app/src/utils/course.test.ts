import { describe, expect, it } from 'vitest'
import { courseEmptyMessage, courseTypeLabel, formatCourseDuration } from './course'

describe('courseTypeLabel', () => {
  it('把契约里的三种类型翻成界面文案', () => {
    expect(courseTypeLabel('ARTICLE')).toBe('图文')
    expect(courseTypeLabel('VIDEO')).toBe('视频')
    expect(courseTypeLabel('TEXT')).toBe('纯文本')
  })

  it('遇到没见过的类型不留空白', () => {
    // 后端将来加类型时前端会先拿到未知值，界面不能出现空标签。
    expect(courseTypeLabel('PODCAST')).toBe('课程')
    expect(courseTypeLabel('')).toBe('课程')
  })
})

describe('formatCourseDuration', () => {
  it('时长允许为空，空值一律不出文案', () => {
    // 契约第 0 节：时长只登记、允许为空、不做任何校验与提示。
    expect(formatCourseDuration(null)).toBe('')
    expect(formatCourseDuration(undefined)).toBe('')
    expect(formatCourseDuration(0)).toBe('')
  })

  it('脏数据不渲染成 NaN', () => {
    expect(formatCourseDuration(-30)).toBe('')
    expect(formatCourseDuration(Number.NaN)).toBe('')
    expect(formatCourseDuration(Number.POSITIVE_INFINITY)).toBe('')
  })

  it('不足一分钟只报秒', () => {
    expect(formatCourseDuration(1)).toBe('1 秒')
    expect(formatCourseDuration(59)).toBe('59 秒')
  })

  it('整分钟不拖一个「0 秒」', () => {
    expect(formatCourseDuration(60)).toBe('1 分钟')
    expect(formatCourseDuration(600)).toBe('10 分钟')
  })

  it('分钟带余秒时两段都报', () => {
    expect(formatCourseDuration(90)).toBe('1 分 30 秒')
    expect(formatCourseDuration(3599)).toBe('59 分 59 秒')
  })

  it('超过一小时按小时收敛，不再报秒', () => {
    expect(formatCourseDuration(3600)).toBe('1 小时')
    expect(formatCourseDuration(3900)).toBe('1 小时 5 分钟')
    // 长视频（这里 5 小时 1 分）不该把秒也堆进标签里。
    expect(formatCourseDuration(18060)).toBe('5 小时 1 分钟')
  })

  it('小数秒向下取整，不出现 12.7 秒', () => {
    expect(formatCourseDuration(12.7)).toBe('12 秒')
    expect(formatCourseDuration(90.9)).toBe('1 分 30 秒')
  })
})

describe('courseEmptyMessage', () => {
  it('筛了合集就说清是这个合集没课，并指路换一个', () => {
    expect(courseEmptyMessage('情绪与认知')).toBe('「情绪与认知」这个合集还没有上架的课程，换个合集看看')
  })

  it('没筛合集就说明整体还没上架，而不是只说「暂无数据」', () => {
    expect(courseEmptyMessage(null)).toBe('课程还在筹备中，上架后会出现在这里')
    expect(courseEmptyMessage('')).toBe('课程还在筹备中，上架后会出现在这里')
  })
})
