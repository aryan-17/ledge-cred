import { Hono } from 'hono'
import { classifySmsMessages } from '../services/gemini'
import type { AppVariables, ClassifyMessage } from '../types'

export const classifyRoute = new Hono<{ Variables: AppVariables }>()

classifyRoute.post('/', async (c) => {
  const { messages } = await c.req.json<{ messages: ClassifyMessage[] }>()

  if (!messages?.length) {
    return c.json({ results: [] })
  }

  const results = await classifySmsMessages(messages)
  return c.json({ results })
})
