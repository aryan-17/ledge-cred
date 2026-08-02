import { getFirebaseApp } from '../lib/firebase'

export async function sendFcmNotification(
  fcmToken: string,
  title: string,
  body: string
): Promise<void> {
  await getFirebaseApp().messaging().send({
    token: fcmToken,
    notification: { title, body },
    android: {
      priority: 'high',
      notification: { channelId: 'settle_digest' }
    }
  })
}
