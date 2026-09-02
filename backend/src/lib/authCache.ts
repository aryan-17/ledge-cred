// ponytail: in-process Map cache, resets on restart — fine for single-instance; replace with Redis if multi-instance
const cache = new Map<string, { uid: string; exp: number }>()

export function getCached(token: string): string | null {
  const hit = cache.get(token)
  if (!hit || Date.now() / 1000 > hit.exp - 60) {
    cache.delete(token)
    return null
  }
  return hit.uid
}

export function setCache(token: string, uid: string, exp: number) {
  cache.set(token, { uid, exp })
  // Periodic GC: sweep expired entries every 100 insertions
  if (cache.size % 100 === 0) {
    const now = Date.now() / 1000
    for (const [k, v] of cache) if (v.exp < now) cache.delete(k)
  }
}
