import { describe, it, expect, vi, beforeEach } from 'vitest'
import { Hono } from 'hono'
import { authMiddleware } from '../../src/middleware/auth'
import { usersRoute } from '../../src/routes/users'

const mockVerifyIdToken = vi.fn()
const mockPrisma = {
  user: { upsert: vi.fn() }
}

vi.mock('../../src/lib/firebase', () => ({
  getFirebaseAuth: vi.fn(() => ({ verifyIdToken: mockVerifyIdToken }))
}))

vi.mock('../../src/lib/prisma', () => ({
  getPrisma: vi.fn(() => mockPrisma)
}))

const app = new Hono()
app.use('*', authMiddleware)
app.route('/users', usersRoute)

describe('POST /users/register', () => {
  beforeEach(() => {
    mockVerifyIdToken.mockResolvedValue({ uid: 'user-123' })
    mockPrisma.user.upsert.mockResolvedValue({ uid: 'user-123', fcmToken: 'tok' })
  })

  it('returns uid on success', async () => {
    const res = await app.request('/users/register', {
      method: 'POST',
      headers: {
        Authorization: 'Bearer valid',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ fcmToken: 'fcm-token-abc' })
    })
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ uid: 'user-123' })
  })

  it('upserts user with fcmToken from body', async () => {
    await app.request('/users/register', {
      method: 'POST',
      headers: {
        Authorization: 'Bearer valid',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ fcmToken: 'fcm-token-abc' })
    })
    expect(mockPrisma.user.upsert).toHaveBeenCalledWith({
      where: { uid: 'user-123' },
      update: { fcmToken: 'fcm-token-abc' },
      create: { uid: 'user-123', fcmToken: 'fcm-token-abc' }
    })
  })
})
