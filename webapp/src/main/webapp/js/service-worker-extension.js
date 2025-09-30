let accessToken = null;
let userId = null;
const CHAT_NOTIFICATION_TYPE = 'CHAT_NOTIFICATION';
const DB_SETTINGS = {
  DB_NAME: 'CHAT',
  DB_VERSION: 5,
  DB_STORES: {
    SETTINGS: 'SETTINGS',
    READ_RECEIPTS: 'READ_RECEIPTS',
  }
};

self.addEventListener('push', event => {
  if (self?.Notification?.permission === 'granted') {
    const data = event?.data?.text?.() || {};
    const params = data.split(':');
    if (params.length === 3) {
      const notificationType = params[0];
      if(notificationType === CHAT_NOTIFICATION_TYPE) {
        const action = params[2];
        const eventAndRoomId = decodeURIComponent(params[1]).split('|');
        const eventId = eventAndRoomId[0];
        const roomId = eventAndRoomId[1];
        if (roomId && eventId) {
          event.waitUntil(new Promise(async (resolve, reject) => {
            try {
              if (action === 'open') {
                processChatNotification(roomId, eventId);
              }
            } catch (e) {
              reject(e);
            }
          }));
        }  
      }
    }
  }
});

self.addEventListener('message', event => {
  if (event.origin === self.location.origin) {
    if (self?.Notification?.permission === 'granted') {
      const data = event?.data || {};
      if(data.type === CHAT_NOTIFICATION_TYPE) {
        if (data.roomId && data.eventId) {
          event.waitUntil(new Promise(async (resolve, reject) => {
            try {
              processChatNotification(data.roomId, data.eventId);
            } catch (e) {
              reject(e);
            }
          }));
        }
      }
    }
  }
});

async function retrieveUserSettings() {
  return retrieveFromDb(DB_SETTINGS.DB_STORES.SETTINGS, 'settings');
}

async function retrieveLastReadObject(roomId) {
  const itemKey = `lastRead::${roomId}`;
  return retrieveFromDb(DB_SETTINGS.DB_STORES.READ_RECEIPTS, itemKey);
}

async function retrieveFromDb(dbStore, itemKey) {
  const request = indexedDB.open(DB_SETTINGS.DB_NAME, DB_SETTINGS.DB_VERSION);
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
    const request = transaction.objectStore(dbStore).get(itemKey);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => resolve(null);
  });
}

async function processChatNotification(roomId, eventId) {
  if (!accessToken) {
    const userSettings = await retrieveUserSettings();
    accessToken = userSettings.access_token;
    userId = userSettings.user_id;
  }
  const lastReadMessageObject = await retrieveLastReadObject(roomId);
  const lastReadMessageTimestamp = lastReadMessageObject && lastReadMessageObject[userId] && lastReadMessageObject[userId].ts || 0;
  let chatNotification = await fetch(`/matrix/rest/matrix/notification/${roomId}/${eventId}/${lastReadMessageTimestamp}`, {
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
