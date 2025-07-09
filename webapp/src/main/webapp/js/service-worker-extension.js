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
              console.log('service worker extension START');
              console.log('room ID is : ',roomId);
              console.log('Event ID is ', eventId);
              console.log('Access token is : ', accessToken);
              let chatNotification = await fetch(`/matrix/rest/matrix/notification/${roomId}/${eventId}`, {
                method: 'PUT',
                credentials: 'include',
                body: accessToken
              }).then(resp => resp.ok && resp.json());
              console.log('The retrieved notification is ', chatNotification);
              if (chatNotification) {
                const title = chatNotification.title;
                chatNotification.icon = self.location.origin + chatNotification.icon;
                chatNotification = prepareNotificationToSend(eventId, chatNotification);
                await self.registration.showNotification(title, chatNotification);
                await refreshBadge();
              }
              console.log('service worker extension END');
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
  console.log('📩 Message received in service worker:', event.data);

  if (event.data.action === 'matrix_access_token') {
    accessToken = event.data.value;
  }
});

