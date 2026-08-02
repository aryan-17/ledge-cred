import { initializeApp, cert, type App } from 'firebase-admin/app'
import { getAuth, type Auth } from 'firebase-admin/auth'
import { getMessaging, type Messaging } from 'firebase-admin/messaging'

let app: App

function getFirebaseApp(): App {
  if (!app) {
    app = initializeApp({
      credential: cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT!))
    })
  }
  return app
}

// ponytail: wrappers exist so tests can mock src/lib/firebase as a single module
export function getFirebaseAuth(): Auth {
  return getAuth(getFirebaseApp())
}

export function getFirebaseMessaging(): Messaging {
  return getMessaging(getFirebaseApp())
}
