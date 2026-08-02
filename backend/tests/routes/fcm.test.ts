import { describe, it, expect, vi, beforeEach } from 'vitest'
import { Hono } from 'hono'
import { authMiddleware } from '../../src/middleware/auth'
import { fcmRoute } from '../../src/routes/fcm'

const mockVerifyIdToken = vi.fn()
const mockFcmSend = vi.fn()
const mockPrisma = {
  user: { update: vi.fn(), findUnique: vi.fn() }
}

vi.mock('../../src/lib/firebase', () => ({
  getFirebaseAuth: vi.fn(() => ({ verifyIdToken: mockVerifyIdToken })),
  getFirebaseMessaging: vi.fn(() => ({ send: mockFcmSend }))
}))

vi.mock('../../src/lib/prisma', () => ({
  getPrisma: vi.fn(() => mockPrisma)
}))

const app = new Hono()
app.use('*', authMiddleware)
app.route('/fcm', fcmRoute)

const AUTH = { Authorization: 'Bearer valid', 'Content-Type': 'application/json' }

describe('PUT /fcm/token', () => {
  beforeEach(() => {
    mockVerifyIdToken.mockResolvedValue({ uid: 'user-123' })
    mockPrisma.user.update.mockResolvedValue({})
  })

  it('updates FCM token for the authenticated user', async () => {
    const res = await app.request('/fcm/token', {
      method: 'PUT',
      headers: AUTH,
      body: JSON.stringify({ token: 'new-fcm-token-xyz' })
    })
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ ok: true })
    expect(mockPrisma.user.update).toHaveBeenCalledWith({
      where: { uid: 'user-123' },
      data: { fcmToken: 'new-fcm-token-xyz' }
    })
  })
})

describe('POST /fcm/notify', () => {
  beforeEach(() => {
    mockVerifyIdToken.mockResolvedValue({ uid: 'user-123' })
    mockFcmSend.mockResolvedValue('projects/x/messages/y')
  })

  it('sends FCM notification using stored token', async () => {
    mockPrisma.user.findUnique.mockResolvedValue({
      uid: 'user-123',
      fcmToken: 'stored-fcm-token'
    })

    const res = await app.request('/fcm/notify', {
      method: 'POST',
      headers: AUTH,
      body: JSON.stringify({ title: 'CC Settle', body: 'Settle ₹42,380 now' })
    })

    expect(res.status).toBe(200)
    expect(mockFcmSend).toHaveBeenCalledWith(
      expect.objectContaining({
        token: 'stored-fcm-token',
        notification: { title: 'CC Settle', body: 'Settle ₹42,380 now' }
      })
    )
  })

  it('returns 400 when no FCM token is registered', async () => {
    mockPrisma.user.findUnique.mockResolvedValue({ uid: 'user-123', fcmToken: null })

    const res = await app.request('/fcm/notify', {
      method: 'POST',
      headers: AUTH,
      body: JSON.stringify({ title: 'Test', body: 'Test' })
    })

    expect(res.status).toBe(400)
    expect(await res.json()).toEqual({ error: 'No FCM token registered' })
  })
})
