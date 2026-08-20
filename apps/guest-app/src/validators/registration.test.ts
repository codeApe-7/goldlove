import { describe, expect, it } from 'vitest'
import {
  isPhoneValid,
  normalizePhone,
  validateRegistrationForm,
  type RegistrationForm,
} from './registration'

const VALID: RegistrationForm = {
  phone: '13800138000',
  password: 'Guest-register-2026',
  confirmPassword: 'Guest-register-2026',
  agreed: true,
}

describe('registration form validation', () => {
  it('accepts a well-formed submission', () => {
    expect(validateRegistrationForm(VALID)).toBeNull()
  })

  it('tolerates spaces and hyphens in the phone number', () => {
    expect(normalizePhone(' 138-0013 8000 ')).toBe('13800138000')
    expect(isPhoneValid(' 138-0013 8000 ')).toBe(true)
    expect(validateRegistrationForm({ ...VALID, phone: '138 0013 8000' })).toBeNull()
  })

  it('rejects phone numbers that are not 11 mainland digits', () => {
    for (const phone of ['1380013800', '138001380000', '12800138000', 'abcdefghijk', '']) {
      expect(isPhoneValid(phone)).toBe(false)
      expect(validateRegistrationForm({ ...VALID, phone })).toBe('请输入 11 位手机号')
    }
  })

  it('enforces the same password policy as the server', () => {
    expect(validateRegistrationForm({ ...VALID, password: 'Short1', confirmPassword: 'Short1' }))
      .toBe('密码需为 12 至 128 位')
    expect(validateRegistrationForm({
      ...VALID,
      password: 'a'.repeat(129) + '1',
      confirmPassword: 'a'.repeat(129) + '1',
    })).toBe('密码需为 12 至 128 位')
    expect(validateRegistrationForm({
      ...VALID,
      password: 'onlyletterspassword',
      confirmPassword: 'onlyletterspassword',
    })).toBe('密码需同时包含字母和数字')
    expect(validateRegistrationForm({
      ...VALID,
      password: '123456789012',
      confirmPassword: '123456789012',
    })).toBe('密码需同时包含字母和数字')
  })

  it('requires both password fields to match', () => {
    expect(validateRegistrationForm({ ...VALID, confirmPassword: 'Different-pass-2026' }))
      .toBe('两次输入的密码不一致')
  })

  it('requires the authorization checkbox', () => {
    expect(validateRegistrationForm({ ...VALID, agreed: false }))
      .toBe('请先阅读并同意授权书')
  })

  it('reports the phone problem first when several fields are wrong', () => {
    expect(validateRegistrationForm({
      phone: '123',
      password: 'x',
      confirmPassword: 'y',
      agreed: false,
    })).toBe('请输入 11 位手机号')
  })
})
