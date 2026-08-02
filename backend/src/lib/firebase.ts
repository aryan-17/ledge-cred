import admin from 'firebase-admin'

let app: admin.app.App

export function getFirebaseApp(): admin.app.App {
  if (!app) {
    app = admin.initializeApp({
      credential: admin.credential.cert(
        JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT!)
      )
    })
  }
  return app
}
