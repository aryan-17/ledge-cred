#!/usr/bin/env node
// Generates N Firebase ID tokens for load testing.
// Uses Admin SDK to mint custom tokens, then exchanges via REST for ID tokens.
//
// Usage:
//   FIREBASE_SERVICE_ACCOUNT='{"type":"service_account",...}' \
//   FIREBASE_WEB_API_KEY=AIza... \
//   node docs/gen-test-tokens.mjs [count=10]
//
// Output: docs/tokens.txt (gitignored), one token per line

import { initializeApp, cert } from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'
import { writeFileSync } from 'fs'
import { resolve } from 'path'

const COUNT = parseInt(process.argv[2] ?? '10', 10)
const SA = process.env.FIREBASE_SERVICE_ACCOUNT
const API_KEY = process.env.FIREBASE_WEB_API_KEY

if (!SA || !API_KEY) {
  console.error('Missing FIREBASE_SERVICE_ACCOUNT or FIREBASE_WEB_API_KEY')
  process.exit(1)
}

initializeApp({ credential: cert(JSON.parse(SA)) })
const auth = getAuth()

async function exchangeCustomToken(customToken) {
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${API_KEY}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: customToken, returnSecureToken: true }),
    }
  )
  if (!res.ok) {
    const err = await res.text()
    throw new Error(`Exchange failed: ${err}`)
  }
  const { idToken } = await res.json()
  return idToken
}

const tokens = []
for (let i = 1; i <= COUNT; i++) {
  const uid = `load-test-user-${i}`
  const customToken = await auth.createCustomToken(uid)
  const idToken = await exchangeCustomToken(customToken)
  tokens.push(idToken)
  console.log(`✓ ${uid}`)
}

const out = resolve('docs/tokens.txt')
writeFileSync(out, tokens.join('\n'))
console.log(`\nWrote ${COUNT} tokens → ${out}`)
console.log('Note: ID tokens expire in 1 hour. Re-run before each test session.')
