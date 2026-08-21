interface BrowserWindow {
  location?: { search?: string; hash?: string; href?: string }
}

function browserWindow(): BrowserWindow {
  return (typeof window === 'undefined' ? {} : window) as unknown as BrowserWindow
}

/**
 * H5 是 hash 路由（`/#/pages/vip/index`），支付渠道把回跳参数追加在整串之后时，
 * 参数会落到 # 里，`location.search` 读不到。因此两处都扫。
 */
function currentQuery(): string {
  const location = browserWindow().location
  const parts: string[] = []
  const search = (location?.search ?? '').replace(/^\?/, '')
  if (search) parts.push(search)
  const hash = location?.hash ?? ''
  const marker = hash.indexOf('?')
  if (marker !== -1) {
    const hashQuery = hash.slice(marker + 1)
    if (hashQuery) parts.push(hashQuery)
  }
  return parts.join('&')
}

/** 从当前地址读取渠道回跳带回的参数。 */
export function readQueryParam(name: string, search?: string): string | null {
  const raw = search ?? currentQuery()
  const query = raw.startsWith('?') ? raw.slice(1) : raw
  for (const pair of query.split('&')) {
    if (!pair) continue
    const separator = pair.indexOf('=')
    const key = separator === -1 ? pair : pair.slice(0, separator)
    if (decodeURIComponent(key) !== name) continue
    return separator === -1 ? '' : decodeURIComponent(pair.slice(separator + 1).replace(/\+/g, ' '))
  }
  return null
}

export function redirectTo(url: string): void {
  const target = browserWindow().location
  if (target) target.href = url
}
