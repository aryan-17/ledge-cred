// Simple in-memory per-UID sliding window rate limiter
// ponytail: in-memory — resets on restart, fine for single-instance Render free tier

const windows = new Map<string, number[]>()

export function rateLimit(uid: string, maxRequests: number, windowMs: number): boolean {
  const now = Date.now()
  const hits = (windows.get(uid) ?? []).filter(t => now - t < windowMs)
  if (hits.length >= maxRequests) return false
  hits.push(now)
  windows.set(uid, hits)
  return true
}
