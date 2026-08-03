import { serve } from '@hono/node-server'
import app from './app'
import log from './lib/logger'

const port = parseInt(process.env.PORT ?? '3000')
serve({ fetch: app.fetch, port }, () => {
  log.info({ port, env: process.env.NODE_ENV ?? 'development' }, 'CC Settle backend started')
})
