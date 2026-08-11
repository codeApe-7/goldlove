import type { GuestProfileDraft } from '@/types'
import type { ProfileValues } from '@/validators/profile'

export const CORE_FIELD_PROPERTIES = {
  gender: 'gender',
  birth_date: 'birthDate',
  height_cm: 'heightCm',
  education: 'education',
  occupation: 'occupation',
  income_range: 'incomeRange',
  city: 'city',
  wechat_id: 'wechatId',
  douyin_id: 'douyinId',
  douyin_nickname: 'douyinNickname',
  douyin_profile_url: 'douyinProfileUrl',
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
    payload[property] = values[fieldCode] ?? ''
  }
  return payload
}
