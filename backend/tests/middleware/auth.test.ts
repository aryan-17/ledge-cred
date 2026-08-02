import { describe, it, expect, vi, beforeEach } from 'vitest'
import { Hono } from 'hono'
import { authMiddleware } from '../../src/middleware/auth'

const mockVerifyIdToken = vi.fn()

vi.mock('../../src/lib/firebase', () => ({
  getFirebaseAuth: vi.fn(() => ({ verifyIdToken: mockVerifyIdToken }))
}))

const testApp = new Hono()
testApp.use('*', authMiddleware)
testApp.get('/test', (c) => c.json({ uid: c.get('uid') }))

describe('authMiddleware', () => {
  beforeEach(() => {
    mockVerifyIdToken.mockResolvedValue({ uid: 'user-123' })
  })

  it('rejects request with no Authorization header', async () => {
    const res = await testApp.request('/test')
    expect(res.status).toBe(401)
    expect(await res.json()).toEqual({ error: 'Missing authorization token' })
  })

  it('rejects non-Bearer scheme', async () => {
    const res = await testApp.request('/test', {
      headers: { Authorization: 'Basic abc123' }
    })
    expect(res.status).toBe(401)
  })

  it('rejects invalid token', async () => {
    mockVerifyIdToken.mockRejectedValue(new Error('invalid token'))
    const res = await testApp.request('/test', {
      headers: { Authorization: 'Bearer bad-token' }
    })
    expect(res.status).toBe(401)
    expect(await res.json()).toEqual({ error: 'Invalid token' })
  })

  it('sets uid in context for valid token', async () => {
    const res = await testApp.request('/test', {
      headers: { Authorization: 'Bearer valid-token' }
    })
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ uid: 'user-123' })
  })
})
