export type ProfileGroup = 'basic' | 'career' | 'social' | 'more'
export type GuestStatusTone = 'neutral' | 'warning' | 'success' | 'danger'

const FIELD_GROUPS: Record<string, ProfileGroup> = {
  gender: 'basic',
  birth_date: 'basic',
  height_cm: 'basic',
  education: 'basic',
  city: 'basic',
  occupation: 'career',
  income_range: 'career',
  wechat_id: 'social',
  douyin_id: 'social',
  douyin_nickname: 'social',
  douyin_profile_url: 'social',
}

export function profileGroup(fieldCode: string): ProfileGroup {
  return FIELD_GROUPS[fieldCode] ?? 'more'
}

export function profileCompletion(
  definitions: ReadonlyArray<{ fieldCode: string }>,
  values: Record<string, unknown>,
  hasAvatar: boolean,
): number {
  const completedFields = definitions.filter(({ fieldCode }) => isFilled(values[fieldCode])).length
  const total = definitions.length + 1
  return total === 0 ? 0 : Math.round(((completedFields + Number(hasAvatar)) / total) * 100)
}

export function remainingLifePhotoSlots(
  photos: ReadonlyArray<{ category: string }>,
): number {
  return Math.max(0, 6 - photos.filter(({ category }) => category === 'LIFE').length)
}

function isFilled(value: unknown): boolean {
  return value !== null && value !== undefined && (typeof value !== 'string' || value.trim() !== '')
}

export function guestStatusMeta(status: string): {
  label: string
  tone: GuestStatusTone
  description: string
} {
  return ({
    DRAFT: { label: '未提交', tone: 'neutral', description: '完善档案后提交审核' },
    PENDING_REVIEW: { label: '审核中', tone: 'warning', description: '档案已提交，请耐心等待审核' },
    APPROVED: { label: '已通过', tone: 'success', description: '恭喜，您的档案已通过审核' },
    CHANGES_REQUESTED: { label: '被退回', tone: 'danger', description: '请按提示修改后重新提交' },
  } as const)[status] ?? { label: '未提交', tone: 'neutral', description: '完善档案后提交审核' }
}
