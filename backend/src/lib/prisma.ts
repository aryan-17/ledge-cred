import { PrismaClient } from '@prisma/client'

let client: PrismaClient

export function getPrisma(): PrismaClient {
  if (!client) {
    client = new PrismaClient()
  }
  return client
}
