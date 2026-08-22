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
    const value = values[fieldCode]
    // 没填的字段必须发 null，不能发空串。后端 douyinProfileUrl 是 URI 类型，
    // Jackson 会把空串反序列化成 URI.create("")（URI 的特例，不是 null），
    // 于是选填的抖音主页链接会被当成「有值但格式不对」直接拒掉。
    payload[property] = value === '' || value === undefined ? null : value
  }
  return payload
}
