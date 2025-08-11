let accessToken = null;
const CHAT_NOTIFICATION_TYPE = 'CHAT_NOTIFICATION';
self.addEventListener('push', event => {
  if (self?.Notification?.permission === 'granted') {
    const data = event?.data?.text?.() || {};
    const params = data.split(':');
    if(params.length == 3) {
      const notificationType = params[0];
      if(notificationType === CHAT_NOTIFICATION_TYPE) {
        const action = params[2];
        const eventAndRoomId = decodeURIComponent(params[1]).split('|');
        const eventId = eventAndRoomId[0];
        const roomId = eventAndRoomId[1];
        event.waitUntil(new Promise(async (resolve, reject) => {
          try {
            if (action === 'open') {
              if (!accessToken) {
                accessToken = await retrieveAccessToken();
              }
              let chatNotification = await fetch(`/matrix/rest/matrix/notification/${roomId}/${eventId}`, {
                method: 'PUT',
                credentials: 'include',
                body: accessToken
              }).then(resp => {
                if(resp?.ok) {
                  return resp.json()
                } else {
                  console.error('could not retrieve the related chat event', resp);
                  return null;
                }
              });
              if (chatNotification) {
                const title = chatNotification.title;
                chatNotification.icon = self.location.origin + chatNotification.icon;
                chatNotification.type = CHAT_NOTIFICATION_TYPE;
                delete chatNotification.actions;
                chatNotification = prepareNotificationToSend(eventId, chatNotification);
                await self.registration.showNotification(title, chatNotification);
                await refreshBadge();
              }
            }
          } catch (e) {
            reject(e);
          }
        }));
      }
    }
  }
});

async function retrieveAccessToken() {
  const dbName ='CHAT';
  const dbStore = 'SETTINGS';
  const dbVersion = 3;
  // Open indexDb
  const request = indexedDB.open(dbName, dbVersion);
  const database = await new Promise((resolve, reject) => {
    request.onerror = reject;
    request.onsuccess = e => resolve(e.target.result);
    request.onupgradeneeded = e => {
      try {
        e.target.result.createObjectStore(dbStore);
      } catch (e) {
        console.debug('Error upgrading database version', e);
        reject(e);
      }
    };
  });
  return new Promise(resolve => {
    const transaction = database.transaction([dbStore], 'readonly');
    const request = transaction.objectStore(dbStore).get('access_token');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => resolve(null);
  });
}
