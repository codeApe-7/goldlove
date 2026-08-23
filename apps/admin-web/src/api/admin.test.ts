import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import {
  activateAccount,
  exportProfiles,
  generateActivationCode,
  listActivationCodes,
  listPaymentOrders,
  listProfiles,
  paymentSetting,
  profileCounts,
  profileDetail,
  revokeActivationCode,
  suspendAccount,
  updatePaymentSetting,
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

    await listProfiles({ keyword: '138', status: 'DRAFT', paidOnly: true, page: 2, size: 20 })

    expect(http.get).toHaveBeenCalledWith('/admin/profiles', {
      params: { keyword: '138', status: 'DRAFT', paidOnly: true, page: 2, size: 20 },
    })
  })

  it('profileCounts hits the counts endpoint', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(
      ok({ total: 2, draft: 1, completed: 1, suspended: 0, paid: 0 }),
    )

    await expect(profileCounts({ keyword: '138' })).resolves.toMatchObject({ total: 2 })
    expect(http.get).toHaveBeenCalledWith('/admin/profiles/counts', {
      params: { keyword: '138' },
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

  it('exportProfiles asks for a blob and joins ids with commas', async () => {
    const blob = new Blob(['﻿档案编号'], { type: 'text/csv' })
    vi.spyOn(http, 'get').mockResolvedValue({ data: blob } as never)

    await expect(exportProfiles({ status: 'DRAFT' }, [7, 9])).resolves.toBe(blob)
    // axios 默认会把数组序列化成 ids[]=7&ids[]=9，Spring 的 List<Long> 收不到；
    // 逗号串它会自己拆开。
    expect(http.get).toHaveBeenCalledWith('/admin/profiles/export', {
      params: { status: 'DRAFT', ids: '7,9' },
      responseType: 'blob',
    })
  })

  it('exportProfiles omits ids when nothing is selected', async () => {
    vi.spyOn(http, 'get').mockResolvedValue({ data: new Blob() } as never)

    await exportProfiles({ status: 'DRAFT' })

    expect(http.get).toHaveBeenCalledWith('/admin/profiles/export', {
      params: { status: 'DRAFT' },
      responseType: 'blob',
    })
  })

  it('suspendAccount and activateAccount post the reason', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(ok({ accountId: 3, status: 'SUSPENDED' }))

    await expect(suspendAccount(3, '资料不实')).resolves.toMatchObject({ status: 'SUSPENDED' })
    expect(http.post).toHaveBeenCalledWith('/admin/accounts/3/suspend', { reason: '资料不实' })

    await activateAccount(3, null)
    expect(http.post).toHaveBeenCalledWith('/admin/accounts/3/activate', { reason: null })
  })

  it('paymentSetting reads the current amount', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(
      ok({ vipUpgradeAmountMinor: 12_800, managedInAdmin: true }),
    )

    await expect(paymentSetting()).resolves.toMatchObject({ vipUpgradeAmountMinor: 12_800 })
    expect(http.get).toHaveBeenCalledWith('/admin/payment-settings')
  })

  it('updatePaymentSetting puts the amount in minor units', async () => {
    vi.spyOn(http, 'put').mockResolvedValue(ok({ vipUpgradeAmountMinor: 12_800 }))

    await expect(updatePaymentSetting(12_800)).resolves.toMatchObject({
      vipUpgradeAmountMinor: 12_800,
    })
    expect(http.put).toHaveBeenCalledWith('/admin/payment-settings', {
      vipUpgradeAmountMinor: 12_800,
    })
  })
})
