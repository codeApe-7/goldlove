import type { GuestProfileDraft } from '@/types'
import type { ProfileValues } from '@/validators/profile'

export const CORE_FIELD_PROPERTIES = {
  gender: 'gender',
  age: 'age',
  height_cm: 'heightCm',
  education: 'education',
  occupation: 'occupation',
  income_range: 'incomeRange',
  city: 'city',
  wechat_id: 'wechatId',
  douyin_id: 'douyinId',
} as const

export function draftToProfileValues(draft: GuestProfileDraft): ProfileValues {
  return Object.fromEntries(
    Object.entries(CORE_FIELD_PROPERTIES).map(([fieldCode, property]) => [
      fieldCode,
      draft[property] ?? '',
    ]),
  )
}

export function profileValuesToDraftPayload(
  values: ProfileValues,
  expectedVersion: number | null,
): Record<string, unknown> {
  const payload: Record<string, unknown> = { expectedVersion }
  for (const [fieldCode, property] of Object.entries(CORE_FIELD_PROPERTIES)) {
    const value = values[fieldCode]
    // 没填的字段必须发 null，不能发空串：后端对空串与 null 的处理并不总是一致，
    // 而「没填」是这里唯一想表达的意思。
    payload[property] = value === '' || value === undefined ? null : value
  }
  return payload
}
