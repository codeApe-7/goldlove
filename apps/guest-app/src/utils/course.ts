/**
 * 课程模块的展示层纯函数：类型文案、时长、空状态说明。
 * 抽出来是为了能单测——页面本身在 jsdom 里跑不起 uni-app 运行时。
 */
import type { CourseContentType } from '@/types'

const TYPE_LABEL: Record<CourseContentType, string> = {
  ARTICLE: '图文',
  VIDEO: '视频',
  TEXT: '纯文本',
}

/** 后端将来加类型时前端先拿到未知值，兜底成「课程」而不是空标签。 */
export function courseTypeLabel(contentType: string): string {
  return TYPE_LABEL[contentType as CourseContentType] ?? '课程'
}

/**
 * 视频时长。契约明确：时长只登记，不校验范围、不提示超范围，允许为空。
 * 所以这里对空值与脏数据一律返回空串，由页面决定不渲染这块标签。
 */
export function formatCourseDuration(seconds: number | null | undefined): string {
  if (typeof seconds !== 'number' || !Number.isFinite(seconds) || seconds < 1) {
    return ''
  }
  const total = Math.floor(seconds)
  if (total < 60) {
    return `${total} 秒`
  }
  if (total < 3600) {
    const minutes = Math.floor(total / 60)
    const rest = total % 60
    return rest === 0 ? `${minutes} 分钟` : `${minutes} 分 ${rest} 秒`
  }
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  return minutes === 0 ? `${hours} 小时` : `${hours} 小时 ${minutes} 分钟`
}

/**
 * 空状态文案要说清「为什么空」：筛了合集是这个合集没上架，
 * 没筛合集是整体还没上架——两种情况下用户该做的事不一样。
 */
export function courseEmptyMessage(collectionName: string | null | undefined): string {
  return collectionName
    ? `「${collectionName}」这个合集还没有上架的课程，换个合集看看`
    : '课程还在筹备中，上架后会出现在这里'
}
