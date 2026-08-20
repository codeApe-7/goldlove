import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import {
  generateActivationCode,
  listActivationCodes,
  listPaymentOrders,
  listProfiles,
  profileDetail,
  revokeActivationCode,
} from './admin'

describe('admin api', () => {
  afterEach(() => vi.restoreAllMocks())

  function ok(data: unknown): never {
    return {
      data: { success: true, code: 'OK', message: '成功', data, requestId: 'r' },
    } as never
  }

  it('listProfiles forwards filters as query params', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok({ items: [], page: 1, size: 20, total: 0 }))

    await listProfiles({ phone: '138', status: 'DRAFT', page: 2, size: 20 })

    expect(http.get).toHaveBeenCalledWith('/admin/profiles', {
      params: { phone: '138', status: 'DRAFT', page: 2, size: 20 },
    })
  })

  it('profileDetail reads a single profile by id', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok({ id: 7, phone: '13800138000' }))

    await expect(profileDetail(7)).resolves.toMatchObject({ phone: '13800138000' })
    expect(http.get).toHaveBeenCalledWith('/admin/profiles/7')
  })

  it('listPaymentOrders forwards filters', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok({ items: [], page: 1, size: 20, total: 0 }))

    await listPaymentOrders({ status: 'PAID', page: 1, size: 20 })

    expect(http.get).toHaveBeenCalledWith('/admin/payment-orders', {
      params: { status: 'PAID', page: 1, size: 20 },
    })
  })

  it('generateActivationCode posts the bound phone and tier', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(
      ok({ id: 1, code: 'LOVE-7K2M-9XQP-4T8B', boundPhone: '13800138000' }),
    )

    await expect(
      generateActivationCode({ boundPhone: '13800138000', grantedTier: 'VIP', note: null }),
    ).resolves.toMatchObject({ code: 'LOVE-7K2M-9XQP-4T8B' })
    expect(http.post).toHaveBeenCalledWith('/admin/activation-codes', {
      boundPhone: '13800138000',
      grantedTier: 'VIP',
      note: null,
    })
  })

  it('listActivationCodes and revokeActivationCode hit the expected paths', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok({ items: [], page: 1, size: 20, total: 0 }))
    vi.spyOn(http, 'post').mockResolvedValue(ok(null))

    await listActivationCodes({ status: 'UNUSED', page: 1, size: 20 })
    await revokeActivationCode(42)

    expect(http.get).toHaveBeenCalledWith('/admin/activation-codes', {
      params: { status: 'UNUSED', page: 1, size: 20 },
    })
    expect(http.post).toHaveBeenCalledWith('/admin/activation-codes/42/revoke')
  })
})
