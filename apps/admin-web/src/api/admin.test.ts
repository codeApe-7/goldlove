import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import {
  currentAuthorizationDocumentVersion,
  provisionGuest,
  reissueCredential,
} from './admin'

describe('admin api', () => {
  afterEach(() => vi.restoreAllMocks())

  function ok(data: unknown): never {
    return {
      data: { success: true, code: 'OK', message: '成功', data, requestId: 'r' },
    } as never
  }

  it('provisionGuest posts minor-unit amount and phone', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(ok({}))
    await provisionGuest({
      phone: '13800138000',
      paymentReference: 'PAY-1',
      amountMinor: 19900,
      paidAt: '2030-07-01T10:00:00Z',
      authorizationDocumentVersion: 'v0.3',
      note: null,
    })
    expect(http.post).toHaveBeenCalledWith('/admin/accounts', {
      phone: '13800138000',
      paymentReference: 'PAY-1',
      amountMinor: 19900,
      paidAt: '2030-07-01T10:00:00Z',
      authorizationDocumentVersion: 'v0.3',
      note: null,
    })
  })

  it('reissueCredential posts phone', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(ok({}))
    await reissueCredential('13800138000')
    expect(http.post).toHaveBeenCalledWith(
      '/admin/accounts/activation-credentials/reissue',
      { phone: '13800138000' },
    )
  })

  it('currentAuthorizationDocumentVersion returns version', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(
      ok({ version: 'v0.3', title: '付费建档与直播内容授权书' }),
    )
    await expect(currentAuthorizationDocumentVersion()).resolves.toMatchObject({
      version: 'v0.3',
    })
  })
})
