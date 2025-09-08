export function isPushNotificationsEnabled(userName) {
  return fetch(`/matrix/rest/matrix/isPushNotificationsEnabled/${userName}`, {
    credentials: 'include',
    method: 'GET',
  }).then(resp => {
    if (!resp?.ok) {
      return false;
    }
    return resp.text();
  });
}

export function updatePushNotificationsSettings(userName, active) {
  const payload = {
                     userName: userName,
                     active: active
                  };
  return fetch(`/matrix/rest/matrix/enablePushNotificationsSettings`, {
    credentials: 'include',
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Could not get the  : Response code indicates a server error', resp);
    }
    return resp.json();
  });
}