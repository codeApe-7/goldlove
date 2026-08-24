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
      .toBe('密码需为 8 至 128 位')
    expect(validateRegistrationForm({
      ...VALID,
      password: 'a'.repeat(129) + '1',
      confirmPassword: 'a'.repeat(129) + '1',
    })).toBe('密码需为 8 至 128 位')
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

  /**
   * 下限 8 位这个数字写在三处：这个校验器、注册页输入框的提示、后端 PasswordPolicy。
   * 只改一处不会报错，症状是「前端说可以、后端却拒绝」。这里钉前两处。
   */
  it('8 位刚好放行，7 位就拒', () => {
    expect(validateRegistrationForm({ ...VALID, password: 'abc12345', confirmPassword: 'abc12345' }))
      .toBeNull()
    expect(validateRegistrationForm({ ...VALID, password: 'abc1234', confirmPassword: 'abc1234' }))
      .toBe('密码需为 8 至 128 位')
  })

  it('注册页的提示文案跟着说 8 位', async () => {
    const page = (await import('../pages/register/index.vue?raw')).default
    expect(page).toContain('8 至 128 位，含字母和数字')
    expect(page).not.toContain('12 至 128')
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
