import type { GuestFieldDefinition } from '@/types'

export type ProfileValues = Record<string, string | number | boolean | null | undefined>

export function validateProfileForm(
  definitions: GuestFieldDefinition[],
  values: ProfileValues,
): string[] {
  const missing: string[] = []
  for (const definition of definitions) {
    if (!definition.required) continue
    const value = values[definition.fieldCode]
    const present = value !== undefined && value !== null && value !== '' && value !== false
    if (!present) {
      missing.push(definition.fieldCode)
    }
  }
  return missing
}
