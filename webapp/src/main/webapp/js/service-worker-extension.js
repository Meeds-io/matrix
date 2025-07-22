let accessToken = null;
self.addEventListener('push', event => {
  if (self?.Notification?.permission === 'granted') {
    const data = event?.data?.text?.() || {};
    const params = data.split(':');
    if(params.length == 3) {
      const notificationType = params[0];
      if(notificationType === 'Chat') {
        const action = params[2];
        const eventAndRoomId = decodeURIComponent(params[1]).split('|');
        const eventId = eventAndRoomId[0];
        const roomId = eventAndRoomId[1];
        event.waitUntil(new Promise(async (resolve, reject) => {
          try {
            if (action === 'open') {
              let chatNotification = await fetch(`/matrix/rest/matrix/notification/${roomId}/${eventId}`, {
                method: 'PUT',
                credentials: 'include',
                body: accessToken
              }).then(resp => resp.ok && resp.json());
              if (chatNotification) {
                const title = chatNotification.title;
                chatNotification.icon = self.location.origin + chatNotification.icon;
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

self.addEventListener('message', event => {
  if (event.origin !== self.location.origin)
    return;
  if (event.data.action === 'matrix_access_token') {
    accessToken = event.data.value;
  }
});

