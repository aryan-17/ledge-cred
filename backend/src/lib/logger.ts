import pino from 'pino'

const logger = pino({
  level: process.env.LOG_LEVEL ?? 'info',
  transport: (process.env.NODE_ENV === 'production' || process.env.RENDER)
    ? undefined                          // plain JSON → Render/Railway captures it
    : {
        target: 'pino-pretty',
        options: {
          colorize: true,
          translateTime: 'HH:MM:ss',
          ignore: 'pid,hostname'
        }
      }
})

export default logger
