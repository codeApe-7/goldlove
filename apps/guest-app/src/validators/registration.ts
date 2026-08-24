const MAINLAND_MOBILE = /^1[3-9]\d{9}$/
const HAS_LETTER = /[A-Za-z]/
const HAS_DIGIT = /\d/
const MIN_PASSWORD_LENGTH = 8
const MAX_PASSWORD_LENGTH = 128

export interface RegistrationForm {
  phone: string
  password: string
  confirmPassword: string
  agreed: boolean
}

/** 去掉用户可能顺手粘进来的空格与连字符。 */
export function normalizePhone(rawPhone: string): string {
  return rawPhone.trim().replace(/[\s-]/g, '')
}

export function isPhoneValid(rawPhone: string): boolean {
  return MAINLAND_MOBILE.test(normalizePhone(rawPhone))
}

/**
 * 注册表单校验。规则与后端 PasswordPolicy / PhoneNormalizer 一致——
 * 这里只是为了让用户少跑一趟网络，后端仍然是权威判定。
 *
 * @return 第一条错误提示；全部通过时为 null
 */
export function validateRegistrationForm(form: RegistrationForm): string | null {
  if (!isPhoneValid(form.phone)) {
    return '请输入 11 位手机号'
  }
  if (
    form.password.length < MIN_PASSWORD_LENGTH
    || form.password.length > MAX_PASSWORD_LENGTH
  ) {
    return '密码需为 8 至 128 位'
  }
  if (!HAS_LETTER.test(form.password) || !HAS_DIGIT.test(form.password)) {
    return '密码需同时包含字母和数字'
  }
  if (form.password !== form.confirmPassword) {
    return '两次输入的密码不一致'
  }
  if (!form.agreed) {
    return '请先阅读并同意授权书'
  }
  return null
}
