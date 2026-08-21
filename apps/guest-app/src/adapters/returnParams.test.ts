import { describe, expect, it, vi } from 'vitest'
import { readQueryParam } from './returnParams'

describe('channel return parameters', () => {
  it('reads parameters out of a plain query string', () => {
    expect(readQueryParam('out_trade_no', '?out_trade_no=OTN-1&trade_status=SUCCESS')).toBe('OTN-1')
    expect(readQueryParam('trade_status', 'out_trade_no=OTN-1&trade_status=TRADE%2FOK')).toBe(
      'TRADE/OK',
    )
    expect(readQueryParam('out_trade_no', '?trade_status=SUCCESS')).toBeNull()
    expect(readQueryParam('out_trade_no', '')).toBeNull()
  })

  it('finds return parameters that landed inside the hash route', () => {
    // H5 走 hash 路由，渠道把参数追加在 /#/pages/vip/index 之后时，
    // location.search 是空的，参数全在 hash 里。
    const location = {
      search: '',
      hash: '#/pages/vip/index?out_trade_no=OTN-1&trade_status=TRADE_SUCCESS',
    }
    vi.spyOn(window, 'location', 'get').mockReturnValue(location as unknown as Location)

    expect(readQueryParam('out_trade_no')).toBe('OTN-1')
    expect(readQueryParam('trade_status')).toBe('TRADE_SUCCESS')
    expect(readQueryParam('missing')).toBeNull()
  })

  it('still reads plain query parameters and prefers them over the hash', () => {
    const location = { search: '?out_trade_no=OTN-SEARCH', hash: '#/pages/vip/index' }
    vi.spyOn(window, 'location', 'get').mockReturnValue(location as unknown as Location)

    expect(readQueryParam('out_trade_no')).toBe('OTN-SEARCH')
  })

  it('returns null when neither search nor hash carries a query', () => {
    const location = { search: '', hash: '#/pages/vip/index' }
    vi.spyOn(window, 'location', 'get').mockReturnValue(location as unknown as Location)

    expect(readQueryParam('out_trade_no')).toBeNull()
  })
})
