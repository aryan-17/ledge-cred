import { describe, it, expect, vi, beforeEach } from 'vitest'
import { Hono } from 'hono'
import { authMiddleware } from '../../src/middleware/auth'
import { classifyRoute } from '../../src/routes/classify'

const mockVerifyIdToken = vi.fn()
const mockGenerateContent = vi.fn()

vi.mock('../../src/lib/firebase', () => ({
  getFirebaseApp: vi.fn(() => ({
    auth: () => ({ verifyIdToken: mockVerifyIdToken })
  }))
}))

vi.mock('@google/generative-ai', () => ({
  GoogleGenerativeAI: vi.fn(() => ({
    getGenerativeModel: vi.fn(() => ({
      generateContent: mockGenerateContent
    }))
  }))
}))

const app = new Hono()
app.use('*', authMiddleware)
app.route('/classify', classifyRoute)

const AUTH = { Authorization: 'Bearer valid', 'Content-Type': 'application/json' }

describe('POST /classify', () => {
  beforeEach(() => {
    mockVerifyIdToken.mockResolvedValue({ uid: 'user-123' })
  })

  it('returns empty results for empty messages array', async () => {
    const res = await app.request('/classify', {
      method: 'POST',
      headers: AUTH,
      body: JSON.stringify({ messages: [] })
    })
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ results: [] })
  })

  it('proxies to Gemini and returns parsed results', async () => {
    mockGenerateContent.mockResolvedValue({
      response: {
        text: () => JSON.stringify([
          { id: 'msg-1', suggestedType: 'DEBIT', confidence: 0.95 }
        ])
      }
    })

    const res = await app.request('/classify', {
      method: 'POST',
      headers: AUTH,
      body: JSON.stringify({
        messages: [{ id: 'msg-1', text: 'INR 2,500 debited from A/c XX1234 at Amazon' }]
      })
    })

    expect(res.status).toBe(200)
    const body = await res.json()
    expect(body.results).toEqual([
      { id: 'msg-1', suggestedType: 'DEBIT', confidence: 0.95 }
    ])
  })

  it('falls back to UNPARSED for unknown type in Gemini response', async () => {
    mockGenerateContent.mockResolvedValue({
      response: {
        text: () => JSON.stringify([
          { id: 'msg-1', suggestedType: 'GIBBERISH', confidence: 0.5 }
        ])
      }
    })

    const res = await app.request('/classify', {
      method: 'POST',
      headers: AUTH,
      body: JSON.stringify({
        messages: [{ id: 'msg-1', text: 'some weird SMS' }]
      })
    })

    const body = await res.json()
    expect(body.results[0].suggestedType).toBe('UNPARSED')
  })

  it('clamps confidence to [0, 1]', async () => {
    mockGenerateContent.mockResolvedValue({
      response: {
        text: () => JSON.stringify([
          { id: 'msg-1', suggestedType: 'DEBIT', confidence: 1.8 }
        ])
      }
    })

    const res = await app.request('/classify', {
      method: 'POST',
      headers: AUTH,
      body: JSON.stringify({
        messages: [{ id: 'msg-1', text: 'test' }]
      })
    })

    const body = await res.json()
    expect(body.results[0].confidence).toBe(1)
  })
})
